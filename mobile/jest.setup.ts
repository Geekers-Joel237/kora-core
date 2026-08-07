// Setup Jest partagé. Étoffé au fil des lots.
import '@testing-library/react-native';

/**
 * MMKV est un module natif (Nitro) : il n'existe pas dans l'environnement de
 * test. On le remplace par une Map en mémoire, ce qui suffit à tout ce que
 * `src/lib/storage/kv.ts` en attend.
 */
jest.mock('react-native-mmkv', () => {
  const store = new Map<string, string | boolean | number>();
  return {
    createMMKV: () => ({
      set: (key: string, value: string | boolean | number) => store.set(key, value),
      getString: (key: string) => {
        const value = store.get(key);
        return typeof value === 'string' ? value : undefined;
      },
      getBoolean: (key: string) => {
        const value = store.get(key);
        return typeof value === 'boolean' ? value : undefined;
      },
      getNumber: (key: string) => {
        const value = store.get(key);
        return typeof value === 'number' ? value : undefined;
      },
      remove: (key: string) => store.delete(key),
      clearAll: () => store.clear(),
    }),
  };
});

/**
 * `expo-crypto` est un module natif : son auto-mock renvoie `undefined`, ce qui
 * priverait silencieusement chaque requête de son identifiant de corrélation
 * et de sa clé d'idempotence — et ferait sortir `useSubmitPayment` en amont,
 * sans aucune erreur visible.
 */
jest.mock('expo-crypto', () => {
  // Le compteur vit dans la fabrique : Jest interdit à un `jest.mock` de
  // référencer une variable extérieure non préfixée par `mock`.
  let counter = 0;
  return {
    randomUUID: () => {
      counter += 1;
      return `00000000-0000-4000-8000-${String(counter).padStart(12, '0')}`;
    },
  };
});

/** Le moteur haptique n'existe pas non plus en test. */
jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn(async () => undefined),
  selectionAsync: jest.fn(async () => undefined),
  notificationAsync: jest.fn(async () => undefined),
  ImpactFeedbackStyle: { Light: 'light', Medium: 'medium', Heavy: 'heavy' },
  NotificationFeedbackType: { Success: 'success', Warning: 'warning', Error: 'error' },
}));
