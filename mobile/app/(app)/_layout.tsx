import { Redirect, Stack } from 'expo-router';

import { useSession } from '@/features/auth/session';

/**
 * Garde d'authentification. Toute route sous `(app)` exige une session.
 *
 * L'expiration **en cours de session** n'est pas traitée ici : le client HTTP
 * la rattrape par rafraîchissement en vol groupé, et ne déconnecte qu'en
 * dernier recours. Cette garde ne couvre que l'accès direct sans session.
 */
export default function AppLayout() {
  const status = useSession((state) => state.status);

  if (status === 'unknown') return null;
  if (status !== 'authenticated') return <Redirect href="/login" />;

  return <Stack screenOptions={{ headerShown: false, animation: 'slide_from_right' }} />;
}
