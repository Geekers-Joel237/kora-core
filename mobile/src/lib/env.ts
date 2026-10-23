/**
 * Accès typé aux variables d'environnement Expo.
 *
 * Rappel de 06-architecture.md §9 : les variables `EXPO_PUBLIC_*` sont
 * incluses dans le bundle. Aucun secret ne transite par ici.
 */

export type AppEnv = 'development' | 'staging' | 'production';

function readEnv(value: string | undefined, fallback: string): string {
  return value && value.length > 0 ? value : fallback;
}

export const env = {
  apiUrl: readEnv(process.env.EXPO_PUBLIC_API_URL, 'http://localhost:8081'),
  appEnv: readEnv(process.env.EXPO_PUBLIC_ENV, 'development') as AppEnv,
} as const;

export const isProduction = env.appEnv === 'production';
