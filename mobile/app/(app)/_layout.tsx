import { StyleSheet, View } from 'react-native';
import { Redirect, Stack } from 'expo-router';

import { AppLock } from '@/features/auth/AppLock';
import { SessionExpiredSheet } from '@/features/auth/SessionExpiredSheet';
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

  return (
    <View style={styles.root}>
      <Stack screenOptions={{ headerShown: false, animation: 'slide_from_right' }} />
      {/* La feuille de session expirée se superpose à la pile sans la démonter :
          le parcours en cours doit survivre à l'expiration — §8.1. */}
      <SessionExpiredSheet />
      {/* Le verrou passe par-dessus tout, y compris la feuille de session. */}
      <AppLock />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
});
