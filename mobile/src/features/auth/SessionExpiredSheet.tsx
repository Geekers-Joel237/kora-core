import { useCallback, useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { router, usePathname } from 'expo-router';

import { Button } from '@/components/action';
import { Sheet } from '@/components/overlay';
import { Spacer, Text } from '@/components/primitives';
import { space } from '@/theme';
import { useSession } from './session';

/**
 * Session expirée en cours d'action — `docs/05-screens.md` §8.1.
 *
 * **Le cas qui trahit le plus vite une application mal construite.** Le client
 * HTTP tente un rafraîchissement unique en vol groupé ; s'il aboutit,
 * l'utilisateur ne voit rien. Ce panneau ne s'ouvre que sur l'échec.
 *
 * L'écran quitté **reste monté derrière** : c'est tout l'intérêt d'une feuille
 * plutôt que d'une redirection. Le chemin courant est capturé au moment où la
 * session tombe, et `verify-otp` y revient au lieu de l'accueil.
 *
 * ⚠️ Un `401` portant `detail: "Invalid PIN"` n'arrive **jamais** ici : la
 * couche HTTP le classe en `INVALID_PIN`, jamais en `TOKEN_EXPIRED`
 * (contrat §5.2).
 */
export function SessionExpiredSheet() {
  const { t } = useTranslation();
  const pathname = usePathname();

  const expired = useSession((state) => state.expired);
  const resumePath = useSession((state) => state.resumePath);
  const markExpired = useSession((state) => state.markExpired);
  const signOut = useSession((state) => state.signOut);

  // Le fournisseur de jetons signale l'expiration sans connaître la route : il
  // vit dans `lib/http`, qui ne doit rien savoir de la navigation. Le chemin
  // est donc renseigné ici, au premier rendu suivant l'expiration.
  useEffect(() => {
    if (expired && resumePath === null && pathname) void markExpired(pathname);
  }, [expired, resumePath, pathname, markExpired]);

  const reconnect = useCallback(() => {
    router.push('/login');
  }, []);

  const leave = useCallback(() => {
    void signOut();
  }, [signOut]);

  return (
    <Sheet
      visible={expired}
      // Pas de fermeture par geste : refermer laisserait une application
      // authentifiée en apparence dont chaque requête échoue.
      onClose={() => undefined}
      title={t('session.expiredTitle')}
      snapRatio={0.42}
    >
      <View style={styles.body}>
        <Text variant="bodyMd" color="secondary">
          {t('session.expiredMessage')}
        </Text>
        <Spacer size={8} />
        <Button label={t('session.signIn')} onPress={reconnect} testID="session-reconnect" />
        <Spacer size={3} />
        <Button
          label={t('session.signOut')}
          variant="ghost"
          onPress={leave}
          testID="session-sign-out"
        />
      </View>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  body: { paddingTop: space[2] },
});
