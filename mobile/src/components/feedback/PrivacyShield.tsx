import { useEffect, useState } from 'react';
import { AppState, StyleSheet, View, type AppStateStatus } from 'react-native';

import { Icon } from '@/components/primitives';
import { useTheme } from '@/theme';

/**
 * Occultation dans le sélecteur d'applications — NFR-44.
 *
 * Le solde ne doit pas rester lisible dans la vignette du multitâche. La
 * couverture s'installe dès l'état `inactive`, avant `background` : c'est
 * pendant `inactive` que le système prend son instantané.
 *
 * **Deux plateformes, deux garanties inégales — à connaître :**
 *
 * - **Android** : la vignette est déjà neutralisée par `FLAG_SECURE`, posé par
 *   `expo-screen-capture` sur les écrans de PIN et d'OTP. Cette couverture s'y
 *   ajoute pour les autres écrans.
 * - **iOS** : l'instantané est pris par le système au moment où l'application
 *   se désactive. Un rendu JavaScript déclenché par `AppState` **peut** arriver
 *   après. La garantie stricte demanderait une vue native posée sur
 *   `applicationWillResignActive`, donc un module natif que le projet n'a pas.
 *
 * `CONTOURNEMENT(indéterminé)` — à remplacer par une vue native au lot 10 si le
 * profilage sur appareil montre que la vignette fuit.
 */
export function PrivacyShield() {
  const theme = useTheme();
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    const handle = (state: AppStateStatus) => setHidden(state !== 'active');
    const subscription = AppState.addEventListener('change', handle);
    return () => subscription.remove();
  }, []);

  if (!hidden) return null;

  return (
    <View
      style={[styles.shield, { backgroundColor: theme.bg.app }]}
      pointerEvents="none"
      testID="privacy-shield"
    >
      <Icon name="lock" size="xl" color={theme.accent.primary} />
    </View>
  );
}

const styles = StyleSheet.create({
  shield: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
