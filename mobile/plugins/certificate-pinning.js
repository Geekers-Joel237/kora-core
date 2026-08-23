/**
 * Épinglage de certificat — constructions pures.
 *
 * `docs/08-quality-bar.md` §6 : « L'épinglage de certificat est actif en
 * production. » Deux plateformes, deux mécanismes **déclaratifs** — donc aucun
 * module natif à écrire, aucun client HTTP à remplacer :
 *
 * - **Android** : `res/xml/network_security_config.xml`, balise `<pin-set>`.
 * - **iOS** : `NSAppTransportSecurity.NSPinnedDomains` de l'`Info.plist`.
 *
 * Les deux attendent l'empreinte **SHA-256 de la clé publique** (SPKI), en
 * base64 — jamais celle du certificat entier : une rotation de certificat
 * conservant la même paire de clés ne doit pas mettre les clients dehors.
 *
 * ```sh
 * openssl s_client -connect api.kora.cm:443 </dev/null 2>/dev/null \
 *   | openssl x509 -pubkey -noout \
 *   | openssl pkey -pubin -outform der \
 *   | openssl dgst -sha256 -binary | base64
 * ```
 *
 * ⚠️ **Toujours au moins deux empreintes** : celle en service et celle de
 * secours. Une seule empreinte transforme un incident de certificat en panne
 * totale du parc installé, sans recours autre qu'une mise à jour du store.
 *
 * Ce module est **pur** : il ne touche à aucun fichier. C'est ce qui le rend
 * testable sans `expo prebuild`.
 */

/**
 * @typedef {object} PinningConfig
 * @property {string} domain            Domaine de l'API de production.
 * @property {string[]} pins            Empreintes SPKI SHA-256, en base64.
 * @property {boolean} [includeSubdomains]
 * @property {string} [expiration]      `AAAA-MM-JJ`. Android relâche l'épinglage passé cette date.
 */

/** Une empreinte SPKI SHA-256 en base64 fait exactement 44 caractères. */
const PIN_PATTERN = /^[A-Za-z0-9+/]{43}=$/;

const MINIMUM_PINS = 2;

/**
 * Lit la configuration et refuse tout ce qui donnerait un épinglage trompeur.
 *
 * Renvoie `null` quand rien n'est configuré : l'absence d'empreintes est un
 * état légitime tant que le domaine de production n'existe pas. Une
 * configuration **présente mais fausse** lève, en revanche : un épinglage qui
 * ne s'applique pas silencieusement est pire que pas d'épinglage du tout.
 *
 * @param {unknown} raw
 * @returns {PinningConfig | null}
 */
function normalizePinningConfig(raw) {
  if (raw === undefined || raw === null) return null;

  if (typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error('certificatePinning doit être un objet.');
  }

  const { domain, pins, includeSubdomains, expiration } = /** @type {any} */ (raw);

  if (!Array.isArray(pins) || pins.length === 0) return null;

  if (typeof domain !== 'string' || domain.trim() === '') {
    throw new Error('certificatePinning.domain est requis dès qu’une empreinte est fournie.');
  }

  if (domain.includes('/') || domain.includes(':')) {
    throw new Error(`certificatePinning.domain doit être un nom d’hôte nu, reçu « ${domain} ».`);
  }

  for (const pin of pins) {
    if (typeof pin !== 'string' || !PIN_PATTERN.test(pin)) {
      throw new Error(
        `Empreinte invalide « ${String(pin)} » : attendu un SHA-256 SPKI en base64 (44 caractères).`,
      );
    }
  }

  if (new Set(pins).size !== pins.length) {
    throw new Error('Deux empreintes identiques ne constituent pas une empreinte de secours.');
  }

  if (pins.length < MINIMUM_PINS) {
    throw new Error(
      'Au moins deux empreintes sont exigées : une en service, une de secours. ' +
        'Une seule empreinte transforme un incident de certificat en panne du parc installé.',
    );
  }

  if (expiration !== undefined && !/^\d{4}-\d{2}-\d{2}$/.test(String(expiration))) {
    throw new Error('certificatePinning.expiration doit être au format AAAA-MM-JJ.');
  }

  return {
    domain: domain.trim(),
    pins: [...pins],
    includeSubdomains: includeSubdomains !== false,
    ...(expiration !== undefined && { expiration: String(expiration) }),
  };
}

/**
 * `res/xml/network_security_config.xml` — Android.
 *
 * `cleartextTrafficPermitted="false"` est indissociable de l'épinglage : sans
 * lui, un attaquant n'a pas à casser l'épinglage, il lui suffit de rétrograder
 * la connexion en HTTP.
 *
 * @param {PinningConfig} config
 * @returns {string}
 */
function buildNetworkSecurityConfig(config) {
  const expiration = config.expiration ? ` expiration="${config.expiration}"` : '';
  const pins = config.pins
    .map((pin) => `      <pin digest="SHA-256">${pin}</pin>`)
    .join('\n');

  return `<?xml version="1.0" encoding="utf-8"?>
<!-- Généré par plugins/with-certificate-pinning.js — ne pas éditer à la main. -->
<network-security-config>
  <domain-config cleartextTrafficPermitted="false">
    <domain includeSubdomains="${config.includeSubdomains ? 'true' : 'false'}">${config.domain}</domain>
    <pin-set${expiration}>
${pins}
    </pin-set>
  </domain-config>
</network-security-config>
`;
}

/**
 * `NSAppTransportSecurity.NSPinnedDomains` — iOS 14+.
 *
 * @param {PinningConfig} config
 * @returns {Record<string, unknown>}
 */
function buildPinnedDomains(config) {
  return {
    [config.domain]: {
      NSIncludesSubdomains: config.includeSubdomains === true,
      NSPinnedCAIdentities: config.pins.map((pin) => ({ 'SPKI-SHA256-BASE64': pin })),
    },
  };
}

module.exports = {
  MINIMUM_PINS,
  PIN_PATTERN,
  buildNetworkSecurityConfig,
  buildPinnedDomains,
  normalizePinningConfig,
};
