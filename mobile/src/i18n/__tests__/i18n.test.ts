import { stateLabel, transactionTypeLabel } from '@/features/shared/labels';
import { loginErrorMessage } from '@/features/auth/messages';
import { formatSectionHeader } from '@/lib/datetime';
import { normalizeHttpError } from '@/lib/http';
import { maxFontScale, type as typeScale, type TypeToken } from '@/theme';
import {
  currentLanguage,
  FALLBACK_LANGUAGE,
  i18n,
  isLanguagePreference,
  LANGUAGES,
  resolveLanguage,
} from '@/i18n';
import en from '../en.json';
import fr from '../fr.json';

type Json = Record<string, unknown>;

/** Aplatit un dictionnaire imbriqué en chemins pointés, pour comparer deux jeux. */
function flatten(value: unknown, prefix = ''): Map<string, string> {
  const out = new Map<string, string>();
  if (typeof value === 'string') {
    out.set(prefix, value);
    return out;
  }
  if (typeof value !== 'object' || value === null) return out;

  for (const [key, child] of Object.entries(value as Json)) {
    for (const [path, leaf] of flatten(child, prefix ? `${prefix}.${key}` : key)) {
      out.set(path, leaf);
    }
  }
  return out;
}

const FR = flatten(fr);
const EN = flatten(en);

/** Les seules valeurs vides admises : une description d'écran de résultat que
 *  le message d'erreur du serveur remplace intégralement. */
const INTENTIONALLY_EMPTY = new Set([
  'result.success.description',
  'result.failed.description',
]);

describe('parité des catalogues — docs/08-quality-bar.md', () => {
  it('ne laisse aucune clé française sans traduction anglaise', () => {
    const missing = [...FR.keys()].filter((key) => !EN.has(key));
    expect(missing).toEqual([]);
  });

  it('ne laisse aucune clé anglaise orpheline', () => {
    const extra = [...EN.keys()].filter((key) => !FR.has(key));
    expect(extra).toEqual([]);
  });

  it('n’expose aucune chaîne vide non intentionnelle', () => {
    for (const [catalogue, entries] of [
      ['fr', FR],
      ['en', EN],
    ] as const) {
      const empty = [...entries.entries()]
        .filter(([key, value]) => value.trim() === '' && !INTENTIONALLY_EMPTY.has(key))
        .map(([key]) => `${catalogue}:${key}`);
      expect(empty).toEqual([]);
    }
  });

  it('conserve les mêmes variables d’interpolation dans les deux langues', () => {
    const variables = (value: string) =>
      [...value.matchAll(/\{\{(\w+)\}\}/g)].map((match) => match[1]).sort();

    for (const [key, french] of FR) {
      expect({ key, vars: variables(EN.get(key) ?? '') }).toEqual({
        key,
        vars: variables(french),
      });
    }
  });
});

describe('résolution de la langue — NFR-60', () => {
  it('respecte un choix explicite', () => {
    expect(resolveLanguage('en', ['fr-FR'])).toBe('en');
    expect(resolveLanguage('fr', ['en-US'])).toBe('fr');
  });

  it('suit l’appareil quand la préférence est « système »', () => {
    expect(resolveLanguage('system', ['en-GB'])).toBe('en');
    // Une étiquette régionale se ramène à sa langue de base.
    expect(resolveLanguage('system', ['fr-CI'])).toBe('fr');
    expect(resolveLanguage('system', ['fr_CI'])).toBe('fr');
  });

  it('replie sur le français, jamais sur l’anglais', () => {
    expect(resolveLanguage('system', ['de-DE', 'it-IT'])).toBe('fr');
    expect(resolveLanguage('system', [])).toBe('fr');
    expect(FALLBACK_LANGUAGE).toBe('fr');
  });

  it('rejette une préférence stockée invalide', () => {
    expect(isLanguagePreference('fr')).toBe(true);
    expect(isLanguagePreference('system')).toBe(true);
    expect(isLanguagePreference('de')).toBe(false);
    expect(isLanguagePreference(null)).toBe(false);
  });
});

describe('bascule de langue — aucune chaîne non traduite', () => {
  afterEach(async () => {
    await i18n.changeLanguage('fr');
  });

  it('traduit les libellés métier, pas seulement les écrans', async () => {
    expect(currentLanguage()).toBe('fr');
    expect(stateLabel('AUTHORIZATION_FAILED').label).toBe('Autorisation refusée');
    expect(transactionTypeLabel('CASH_IN')).toBe('Dépôt');

    await i18n.changeLanguage('en');

    expect(currentLanguage()).toBe('en');
    expect(stateLabel('AUTHORIZATION_FAILED').label).toBe('Authorisation declined');
    expect(transactionTypeLabel('CASH_IN')).toBe('Deposit');
  });

  it('traduit les messages d’erreur, y compris hors composant', async () => {
    const wrongPin = normalizeHttpError(
      401,
      { status: 401, detail: 'Invalid PIN' },
      { path: '/auth/login', isMoneyMovement: false },
    );

    expect(loginErrorMessage(wrongPin)).toBe('E-mail ou PIN incorrect.');
    await i18n.changeLanguage('en');
    expect(loginErrorMessage(wrongPin)).toBe('Incorrect email or PIN.');
  });

  it('suit la langue jusque dans les dates', async () => {
    const today = new Date();
    expect(formatSectionHeader(today)).toBe('Aujourd’hui');
    await i18n.changeLanguage('en');
    expect(formatSectionHeader(today)).toBe('Today');
  });

  it('laisse un état inconnu en clair plutôt que de le traduire — règle R2', async () => {
    for (const language of LANGUAGES) {
      await i18n.changeLanguage(language);
      expect(stateLabel('PARTIALLY_REFUNDED').label).toBe('PARTIALLY_REFUNDED');
      expect(transactionTypeLabel('CRYPTO_SWAP')).toBe('CRYPTO_SWAP');
    }
  });
});

describe('mise à l’échelle des polices — jusqu’à 200 %', () => {
  it('laisse corps, libellés et mono monter à 200 %', () => {
    const scalable: TypeToken[] = ['bodyLg', 'bodyMd', 'bodySm', 'labelMd', 'labelSm', 'monoMd'];
    for (const token of scalable) {
      expect(maxFontScale[token]).toBeGreaterThanOrEqual(2);
    }
  });

  it('plafonne les tailles d’affichage — c’est ce qui empêche un montant d’être tronqué', () => {
    for (const token of ['displayXl', 'displayLg', 'displayMd'] as TypeToken[]) {
      expect(maxFontScale[token]).toBeLessThanOrEqual(1.3);
    }
  });

  it('couvre chaque variante typographique', () => {
    for (const token of Object.keys(typeScale) as TypeToken[]) {
      expect(maxFontScale[token]).toBeGreaterThan(1);
    }
  });
});
