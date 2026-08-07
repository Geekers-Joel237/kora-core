import { useEffect } from 'react';
import { Redirect } from 'expo-router';

import { useSession } from '@/features/auth/session';
import { KvKey, kvGetBoolean } from '@/lib/storage/kv';

/**
 * Portail de session — `docs/05-screens.md` §1.
 *
 * Aucun rendu visible : l'écran de lancement natif reste affiché jusqu'à la
 * résolution. Un clignotement d'écran intermédiaire ici est immédiatement
 * perceptible et donne l'impression d'une application qui hésite.
 */
export default function GateScreen() {
  const status = useSession((state) => state.status);
  const bootstrap = useSession((state) => state.bootstrap);

  useEffect(() => {
    if (status === 'unknown') void bootstrap();
  }, [status, bootstrap]);

  if (status === 'unknown') return null;

  if (status === 'authenticated') return <Redirect href="/home" />;

  return kvGetBoolean(KvKey.onboardingSeen)
    ? <Redirect href="/login" />
    : <Redirect href="/onboarding" />;
}
