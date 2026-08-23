import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { AccessibilityInfo, useColorScheme } from 'react-native';

import { KvKey, kvGetString, kvSetString } from '@/lib/storage/kv';
import { darkTheme, lightTheme, type Theme } from './tokens';

/**
 * Le sombre est la norme, le clair est l'alternative — `docs/02-design-system.md` §1.1.
 * `system` suit le réglage de l'appareil ; le repli est `dark`, jamais `light`.
 */
export type ThemePreference = 'system' | 'dark' | 'light';

interface ThemeContextValue {
  theme: Theme;
  scheme: 'dark' | 'light';
  preference: ThemePreference;
  setPreference: (preference: ThemePreference) => void;
  /** Vrai quand « réduire les animations » est actif — §7.2. */
  reduceMotion: boolean;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function readStoredPreference(): ThemePreference {
  const stored = kvGetString(KvKey.themePreference);
  return stored === 'dark' || stored === 'light' || stored === 'system' ? stored : 'system';
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const systemScheme = useColorScheme();
  const [preference, setPreferenceState] = useState<ThemePreference>(readStoredPreference);
  const [reduceMotion, setReduceMotion] = useState(false);

  useEffect(() => {
    let cancelled = false;

    void AccessibilityInfo.isReduceMotionEnabled().then((enabled) => {
      if (!cancelled) setReduceMotion(enabled);
    });

    const subscription = AccessibilityInfo.addEventListener(
      'reduceMotionChanged',
      setReduceMotion,
    );

    return () => {
      cancelled = true;
      subscription.remove();
    };
  }, []);

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next);
    kvSetString(KvKey.themePreference, next);
  }, []);

  const value = useMemo<ThemeContextValue>(() => {
    // Repli sur le sombre : `useColorScheme` renvoie `null` avant résolution,
    // et un flash de blanc sur une app sombre est immédiatement perceptible.
    const resolved: 'dark' | 'light' =
      preference === 'system' ? (systemScheme === 'light' ? 'light' : 'dark') : preference;

    return {
      theme: resolved === 'light' ? lightTheme : darkTheme,
      scheme: resolved,
      preference,
      setPreference,
      reduceMotion,
    };
  }, [preference, systemScheme, setPreference, reduceMotion]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): Theme {
  return useThemeContext().theme;
}

export function useThemeContext(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme doit être utilisé à l’intérieur de <ThemeProvider>');
  }
  return context;
}

/**
 * §7.2 — quand « réduire les animations » est actif, translations et mises à
 * l'échelle deviennent des fondus. L'haptique, elle, est **conservée** :
 * la supprimer retirerait du retour à qui en a le plus besoin.
 */
export function useReduceMotion(): boolean {
  return useThemeContext().reduceMotion;
}
