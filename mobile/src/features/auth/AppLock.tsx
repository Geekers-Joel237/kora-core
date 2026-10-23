import { useCallback, useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/action';
import { Icon, Spacer, Text } from '@/components/primitives';
import { space, useTheme } from '@/theme';
import { probeBiometrics, unlockApp, useBiometrics } from './biometrics';

/**
 * Écran de verrouillage — `docs/05-screens.md` §8.3.
 *
 * **Opaque et plein écran, pas une feuille.** Son rôle est de cacher le solde
 * et l'historique à quelqu'un qui a récupéré un téléphone déverrouillé : une
 * surface translucide ou refermable d'un geste ne servirait à rien.
 *
 * Il ne ferme pas la session. Les jetons restent valides, le parcours en cours
 * reste monté derrière : lever le verrou rend l'application exactement dans
 * l'état où elle a été quittée.
 */
export function AppLock() {
  const { t } = useTranslation();
  const theme = useTheme();
  const locked = useBiometrics((state) => state.locked);

  const unlock = useCallback(() => {
    void unlockApp();
  }, []);

  // Sonde une fois au montage : une empreinte peut avoir été retirée depuis
  // l'installation, et une option devenue impossible ne doit pas enfermer.
  useEffect(() => {
    void probeBiometrics();
  }, []);

  // La demande part dès l'apparition du verrou : obliger à appuyer sur un
  // bouton d'abord ajoute un geste sans rien protéger de plus.
  useEffect(() => {
    if (locked) void unlockApp();
  }, [locked]);

  if (!locked) return null;

  return (
    <View
      style={[styles.container, { backgroundColor: theme.bg.app }]}
      accessibilityViewIsModal
      testID="app-lock"
    >
      <Icon name="lock" size="xl" color={theme.accent.primary} />
      <Spacer size={6} />
      <Text variant="titleMd" align="center">
        {t('session.lockedTitle')}
      </Text>
      <Spacer size={8} />
      <Button
        label={t('session.unlock')}
        onPress={unlock}
        icon="fingerprint"
        fullWidth={false}
        testID="app-unlock"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: space[6],
  },
});
