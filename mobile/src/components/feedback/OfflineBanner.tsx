import { StyleSheet, View } from 'react-native';
import Animated, { FadeInUp, FadeOutUp } from 'react-native-reanimated';

import { useTranslation } from 'react-i18next';

import { Icon, Text } from '@/components/primitives';
import { useNetwork } from '@/lib/network';
import { space, useTheme } from '@/theme';

/**
 * Bandeau hors-ligne — NFR-30.
 *
 * **Jamais modal.** Une perte de réseau ne doit pas bloquer la consultation :
 * l'utilisateur garde accès à ses données en cache. Une boîte de dialogue ici
 * transformerait une dégradation en panne.
 *
 * Il pousse le contenu vers le bas plutôt que de le recouvrir : masquer une
 * ligne d'historique pour annoncer une coupure serait un mauvais échange.
 */
export function OfflineBanner() {
  const { t } = useTranslation();
  const theme = useTheme();
  const offline = useNetwork((state) => state.offline);

  if (!offline) return null;

  return (
    <Animated.View
      entering={FadeInUp.springify().damping(20).stiffness(200)}
      exiting={FadeOutUp.duration(200)}
      style={[styles.banner, { backgroundColor: theme.status.reversed.bg }]}
      accessibilityRole="alert"
    >
      <View style={styles.content}>
        <Icon name="wifi-off" size="xs" color={theme.status.reversed.fg} />
        <Text variant="labelMd" tint={theme.status.reversed.fg}>
          {t('feedback.offline')}
        </Text>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  banner: { paddingVertical: space[2], paddingHorizontal: space[4] },
  content: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space[2] },
});
