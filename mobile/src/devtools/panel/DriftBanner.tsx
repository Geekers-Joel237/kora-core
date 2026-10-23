import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon, Pressable, Text } from '@/components/primitives';
import { useStartupDrift } from '@/devtools/drift/useStartupDrift';
import { radius, space, useTheme } from '@/theme';
import { openDevtools } from './store';

/**
 * Bannière de dérive bloquante — `docs/10-validation-mode.md` §5.
 *
 * « Un écart bloquant n'empêche pas l'app de démarrer. Il l'annonce,
 * **bruyamment**, et laisse continuer. » D'où une bannière persistante et non
 * refermable plutôt qu'un `Toast` : un endpoint disparu ne doit pas s'effacer
 * au bout de trois secondes.
 *
 * Les avertissements et les informations ne remontent pas ici — ils vivent dans
 * l'onglet. Rendre visible ce qui n'est pas bloquant apprendrait à ignorer la
 * bannière, et c'est exactement ce qu'on ne peut pas se permettre.
 */
export function DriftBanner() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const report = useStartupDrift();

  if (!report || report.blocking === 0) return null;

  return (
    <View style={[styles.wrapper, { top: insets.top + space[2] }]} pointerEvents="box-none">
      <Pressable
        onPress={() => openDevtools('drift')}
        haptic="tap"
        scale="card"
        accessibilityLabel={`${report.blocking} écart de contrat bloquant, ouvrir le détail`}
        testID="drift-banner"
      >
        <View
          style={[
            styles.banner,
            { backgroundColor: theme.status.failed.bg, borderRadius: radius.sm },
          ]}
        >
          <Icon name="alert-triangle" size="xs" color={theme.status.failed.fg} />
          <Text variant="labelMd" tint={theme.status.failed.fg} numberOfLines={1}>
            Contrat : {report.blocking} écart{report.blocking > 1 ? 's' : ''} bloquant
            {report.blocking > 1 ? 's' : ''}
          </Text>
        </View>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { position: 'absolute', left: space[4], right: space[4] },
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: space[2],
    paddingVertical: space[2],
    paddingHorizontal: space[3],
  },
});
