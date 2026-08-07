import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

import { Icon, Text } from '@/components/primitives';
import { OUTCOME_LABELS, stateLabel } from '@/features/shared/labels';
import { radius, space, useReduceMotion, useTheme } from '@/theme';
import { outcomeOf, type OutcomeGroup } from '@/types/domain';
import type { IconName } from '@/components/primitives';

export interface StatusChipProps {
  /** État brut du backend — peut valoir une valeur encore inconnue (R2). */
  state: string;
  size?: 'sm' | 'md';
  showIcon?: boolean;
  /** Affiche le libellé précis de l'état plutôt que celui de sa famille. */
  detailed?: boolean;
}

const ICONS: Record<OutcomeGroup, IconName> = {
  pending: 'clock',
  success: 'check-circle',
  failed: 'x-circle',
  reversed: 'rotate-ccw',
};

const PULSE_MS = 800;
const DOT_SIZE = 6;

/**
 * Puce d'état. Résout couleur et icône via l'unique table du §2.4 du design
 * system — aucun écran ne définit sa propre correspondance.
 */
export function StatusChip({
  state,
  size = 'sm',
  showIcon = false,
  detailed = false,
}: StatusChipProps) {
  const theme = useTheme();
  const outcome = outcomeOf(state);
  const colors = theme.status[outcome];
  const label = detailed ? stateLabel(state).label : OUTCOME_LABELS[outcome];

  return (
    <View
      style={[
        styles.chip,
        {
          backgroundColor: colors.bg,
          borderRadius: radius.xs,
          paddingVertical: size === 'md' ? space[1] : 0,
          paddingHorizontal: size === 'md' ? space[3] : space[2],
        },
      ]}
    >
      {outcome === 'pending' && <PulsingDot color={colors.fg} />}
      {showIcon && <Icon name={ICONS[outcome]} size="xs" color={colors.fg} />}
      <Text variant={size === 'md' ? 'labelMd' : 'labelSm'} tint={colors.fg}>
        {label}
      </Text>
    </View>
  );
}

/** Un état en cours pulse : c'est ce qui le distingue d'un état figé. */
function PulsingDot({ color }: { color: string }) {
  const opacity = useSharedValue(1);
  const reduceMotion = useReduceMotion();

  useEffect(() => {
    if (reduceMotion) return;
    opacity.value = withRepeat(
      withSequence(
        withTiming(0.3, { duration: PULSE_MS }),
        withTiming(1, { duration: PULSE_MS }),
      ),
      -1,
      false,
    );
  }, [opacity, reduceMotion]);

  const animatedStyle = useAnimatedStyle(() => ({ opacity: opacity.value }));

  return (
    <Animated.View
      style={[
        { width: DOT_SIZE, height: DOT_SIZE, borderRadius: radius.full, backgroundColor: color },
        animatedStyle,
      ]}
    />
  );
}

const styles = StyleSheet.create({
  chip: { flexDirection: 'row', alignItems: 'center', gap: space[1], alignSelf: 'flex-start' },
});
