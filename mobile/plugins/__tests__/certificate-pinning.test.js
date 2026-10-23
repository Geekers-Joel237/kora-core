const {
  MINIMUM_PINS,
  buildNetworkSecurityConfig,
  buildPinnedDomains,
  normalizePinningConfig,
} = require('../certificate-pinning');

/** Empreintes SPKI SHA-256 valides en forme, fabriquées pour le test. */
const PIN_A = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=';
const PIN_B = 'BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=';

const VALID = { domain: 'api.kora.cm', pins: [PIN_A, PIN_B] };

describe('lecture de la configuration d’épinglage — docs/08-quality-bar.md §6', () => {
  it('accepte l’absence de configuration : le domaine de production n’existe pas encore', () => {
    expect(normalizePinningConfig(undefined)).toBeNull();
    expect(normalizePinningConfig(null)).toBeNull();
    expect(normalizePinningConfig({ domain: 'api.kora.cm', pins: [] })).toBeNull();
  });

  it('exige au moins une empreinte de secours', () => {
    // Une seule empreinte transforme un incident de certificat en panne du parc
    // installé, sans recours autre qu'une mise à jour du store.
    expect(MINIMUM_PINS).toBe(2);
    expect(() => normalizePinningConfig({ domain: 'api.kora.cm', pins: [PIN_A] })).toThrow(
      /deux empreintes/i,
    );
  });

  it('refuse deux fois la même empreinte', () => {
    expect(() =>
      normalizePinningConfig({ domain: 'api.kora.cm', pins: [PIN_A, PIN_A] }),
    ).toThrow(/identiques/i);
  });

  it('refuse une empreinte qui n’est pas un SHA-256 SPKI en base64', () => {
    for (const pin of ['pas-une-empreinte', '', 'AAAA=', 123, null]) {
      expect(() => normalizePinningConfig({ domain: 'api.kora.cm', pins: [pin, PIN_B] })).toThrow(
        /empreinte invalide/i,
      );
    }
  });

  it('refuse une URL là où un nom d’hôte est attendu', () => {
    for (const domain of ['https://api.kora.cm', 'api.kora.cm:443', 'api.kora.cm/v1']) {
      expect(() => normalizePinningConfig({ ...VALID, domain })).toThrow(/nom d’hôte/i);
    }
  });

  it('refuse une configuration sans domaine dès qu’une empreinte est fournie', () => {
    // Silencieusement ignorer ce cas produirait un épinglage inactif que
    // personne ne remarquerait avant l'incident.
    expect(() => normalizePinningConfig({ pins: [PIN_A, PIN_B] })).toThrow(/domain/);
  });

  it('valide le format de la date d’expiration', () => {
    expect(() => normalizePinningConfig({ ...VALID, expiration: '01/01/2027' })).toThrow(
      /AAAA-MM-JJ/,
    );
    expect(normalizePinningConfig({ ...VALID, expiration: '2027-01-01' })?.expiration).toBe(
      '2027-01-01',
    );
  });

  it('inclut les sous-domaines par défaut', () => {
    expect(normalizePinningConfig(VALID)?.includeSubdomains).toBe(true);
    expect(normalizePinningConfig({ ...VALID, includeSubdomains: false })?.includeSubdomains).toBe(
      false,
    );
  });
});

describe('configuration Android', () => {
  const xml = buildNetworkSecurityConfig(normalizePinningConfig(VALID));

  it('épingle les deux empreintes en SHA-256', () => {
    expect(xml).toContain(`<pin digest="SHA-256">${PIN_A}</pin>`);
    expect(xml).toContain(`<pin digest="SHA-256">${PIN_B}</pin>`);
  });

  it('interdit le trafic en clair sur le domaine épinglé', () => {
    // Sans cela, l'attaquant n'a pas à casser l'épinglage : il rétrograde en HTTP.
    expect(xml).toContain('cleartextTrafficPermitted="false"');
  });

  it('porte le domaine et l’inclusion des sous-domaines', () => {
    expect(xml).toContain('<domain includeSubdomains="true">api.kora.cm</domain>');
  });

  it('n’écrit une expiration que lorsqu’elle est configurée', () => {
    expect(xml).not.toContain('expiration=');
    const dated = buildNetworkSecurityConfig(
      normalizePinningConfig({ ...VALID, expiration: '2027-01-01' }),
    );
    expect(dated).toContain('<pin-set expiration="2027-01-01">');
  });
});

describe('configuration iOS', () => {
  it('déclare le domaine épinglé au format attendu par NSPinnedDomains', () => {
    expect(buildPinnedDomains(normalizePinningConfig(VALID))).toEqual({
      'api.kora.cm': {
        NSIncludesSubdomains: true,
        NSPinnedCAIdentities: [
          { 'SPKI-SHA256-BASE64': PIN_A },
          { 'SPKI-SHA256-BASE64': PIN_B },
        ],
      },
    });
  });
});
