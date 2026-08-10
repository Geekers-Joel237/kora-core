/**
 * Internationalisation — NFR-60, `docs/06-architecture.md` §2.
 *
 * **Français par défaut, anglais en second, dès la V1.** Le repli n'est pas
 * l'anglais : un utilisateur ivoirien à qui l'application parlerait anglais
 * parce qu'une clé manque serait plus mal servi qu'avec une clé française.
 *
 * Deux principes de mise en œuvre :
 *
 * 1. **Une seule instance, initialisée au chargement du module.** Les modules
 *    non-React — `errors.ts`, `labels.ts`, `messages.ts` — appellent `t()`
 *    directement ; ils ne peuvent pas attendre un `useEffect`.
 * 2. **La préférence est une donnée, la langue résolue en est la conséquence.**
 *    `system` suit l'appareil ; sa résolution est une fonction pure, testable
 *    sans couche native.
 */

// Import nommé plutôt que par défaut : l'export par défaut de i18next porte les
// mêmes membres, et `import/no-named-as-default-member` signale — à raison —
// l'ambiguïté que crée `i18next.t` face à l'export nommé `t`.
import { createInstance } from 'i18next';
import { initReactI18next } from 'react-i18next';
import { getLocales } from 'expo-localization';
import { enUS, fr as frDateLocale } from 'date-fns/locale';
import type { Locale } from 'date-fns';

import { KvKey, kvGetString, kvSetString } from '@/lib/storage/kv';
import en from './en.json';
import fr from './fr.json';

export const LANGUAGES = ['fr', 'en'] as const;
export type Language = (typeof LANGUAGES)[number];

/** `system` est une préférence, jamais une langue : elle se résout. */
export type LanguagePreference = 'system' | Language;

export const FALLBACK_LANGUAGE: Language = 'fr';

const DATE_LOCALES: Record<Language, Locale> = { fr: frDateLocale, en: enUS };

/**
 * Résolution d'une préférence en langue effective — **fonction pure**.
 *
 * Une étiquette régionale (`fr-CI`, `en-GB`) est ramenée à sa langue de base :
 * l'application ne distingue pas les variantes régionales, et `fr-CI` doit
 * évidemment donner du français.
 */
export function resolveLanguage(
  preference: LanguagePreference,
  deviceTags: readonly string[] = [],
): Language {
  if (preference !== 'system') return preference;

  for (const tag of deviceTags) {
    const base = tag.toLowerCase().split(/[-_]/)[0];
    const match = LANGUAGES.find((language) => language === base);
    if (match) return match;
  }

  return FALLBACK_LANGUAGE;
}

export function isLanguagePreference(value: string | null): value is LanguagePreference {
  return value === 'system' || value === 'fr' || value === 'en';
}

/**
 * Étiquettes de langue de l'appareil.
 *
 * `expo-localization` est un module natif : sous jest il est simulé et peut ne
 * rien renvoyer. Un échec ici doit produire le repli, jamais une exception au
 * chargement du module — ce qui rendrait toute l'application inutilisable.
 */
function deviceLanguageTags(): string[] {
  try {
    return getLocales()
      .map((locale) => locale.languageTag)
      .filter((tag): tag is string => typeof tag === 'string');
  } catch {
    return [];
  }
}

function readStoredPreference(): LanguagePreference {
  try {
    const stored = kvGetString(KvKey.languagePreference);
    return isLanguagePreference(stored) ? stored : 'system';
  } catch {
    return 'system';
  }
}

const initialPreference = readStoredPreference();

/**
 * Instance dédiée plutôt que le singleton du module : deux bibliothèques
 * chargeant i18next partageraient sinon la même configuration.
 */
const instance = createInstance();

void instance.use(initReactI18next).init({
  resources: { fr: { translation: fr }, en: { translation: en } },
  lng: resolveLanguage(initialPreference, deviceLanguageTags()),
  fallbackLng: FALLBACK_LANGUAGE,
  // React échappe déjà tout ce qu'il rend ; le faire deux fois transformerait
  // « l'opérateur » en « l&#39;opérateur ».
  interpolation: { escapeValue: false },
  returnNull: false,
});

export const i18n = instance;

/** Traduction hors composant. Même instance que `useTranslation()`. */
export const t = instance.t.bind(instance);

export function currentLanguage(): Language {
  const base = instance.resolvedLanguage ?? instance.language ?? FALLBACK_LANGUAGE;
  return LANGUAGES.find((language) => language === base) ?? FALLBACK_LANGUAGE;
}

/** Locale `date-fns` correspondant à la langue active — voir `lib/datetime.ts`. */
export function dateLocale(): Locale {
  return DATE_LOCALES[currentLanguage()];
}

/**
 * Change la langue et la mémorise.
 *
 * Le changement est immédiat et sans redémarrage : `react-i18next` re-rend tout
 * l'arbre. Les dates suivent parce qu'elles lisent `dateLocale()` au rendu, pas
 * une locale capturée au chargement.
 */
export function setLanguagePreference(preference: LanguagePreference): void {
  kvSetString(KvKey.languagePreference, preference);
  void instance.changeLanguage(resolveLanguage(preference, deviceLanguageTags()));
}

export function languagePreference(): LanguagePreference {
  return readStoredPreference();
}
