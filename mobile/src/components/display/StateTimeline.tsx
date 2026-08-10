import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { useTranslation } from 'react-i18next';

import { Text } from '@/components/primitives';
import { stateLabel } from '@/features/shared/labels';
import { formatTimelineTimestamp } from '@/lib/datetime';
import { radius, space, spring, timing, useReduceMotion, useTheme } from '@/theme';
import { isTerminalState, outcomeOf, type StateTransition } from '@/types/domain';

export interface StateTimelineProps {
  history: StateTransition[];
  /** État courant : sert à savoir si la frise est encore ouverte. */
  currentState: string;
}

const NODE_SIZE = 12;
const CURRENT_NODE_SIZE = 16;
const SEGMENT_WIDTH = 2;
const SEGMENT_HEIGHT = 28;
const NODE_STAGGER_MS = 80;
const CURRENT_PULSE_MS = 800;

/**
 * **Le composant signature de l'application.**
 *
 * Le backend expose déjà un historique d'états horodaté ; la plupart des
 * wallets le gardent pour eux. L'afficher comme une frise vivante est le
 * différenciateur produit de Kora — voir `docs/00-requirements.md` §1.
 *
 * Le pouls continu du nœud courant est ce qui transforme une donnée d'audit en
 * information vivante. Sans lui, la frise n'est qu'un tableau.
 */
export function StateTimeline({ history, currentState }: StateTimelineProps) {
  const terminal = isTerminalState(currentState);

  return (
    <View>
      {history.map((transition, index) => (
        <Node
          key={`${transition.to}-${transition.occurredAt.getTime()}-${index}`}
          transition={transition}
          index={index}
          isLast={index === history.length - 1}
          /** Le dernier nœud d'une opération non terminée pulse en boucle. */
          isCurrent={index === history.length - 1 && !terminal}
        />
      ))}
    </View>
  );
}

function Node({
  transition,
  index,
  isLast,
  isCurrent,
}: {
  transition: StateTransition;
  index: number;
  isLast: boolean;
  isCurrent: boolean;
}) {
  const theme = useTheme();
  const reduceMotion = useReduceMotion();
  const scale = useSharedValue(0);
  const pulse = useSharedValue(1);

  const outcome = outcomeOf(transition.to);
  const failed = outcome === 'failed';
  // Souscription au changement de langue — voir `features/shared/labels.ts`.
  useTranslation();
  const { label, description } = stateLabel(transition.to);

  const color = failed ? theme.status.failed.fg : theme.accent.primary;

  useEffect(() => {
    if (reduceMotion) {
      scale.value = 1;
      return;
    }
    // Apparition en cascade de haut en bas — §6.7.
    scale.value = withDelay(index * NODE_STAGGER_MS, withSpring(1, spring.bouncy));
  }, [index, reduceMotion, scale]);

  useEffect(() => {
    if (!isCurrent || reduceMotion) return;
    pulse.value = withRepeat(
      withSequence(
        withTiming(1.1, { duration: CURRENT_PULSE_MS }),
        withTiming(1, { duration: CURRENT_PULSE_MS }),
      ),
      -1,
      false,
    );
  }, [isCurrent, reduceMotion, pulse]);

  const nodeStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value * (isCurrent ? pulse.value : 1) }],
  }));

  const segmentStyle = useAnimatedStyle(() => ({
    opacity: withDelay(index * NODE_STAGGER_MS, withTiming(1, { duration: timing.normal })),
  }));

  const size = isCurrent ? CURRENT_NODE_SIZE : NODE_SIZE;

  return (
    <View style={styles.row}>
      <View style={styles.rail}>
        {isCurrent && (
          <View
            style={[
              styles.halo,
              { backgroundColor: theme.accent.wash, borderRadius: radius.full },
            ]}
          />
        )}
        <Animated.View
          style={[
            {
              width: size,
              height: size,
              borderRadius: radius.full,
              backgroundColor: color,
            },
            nodeStyle,
          ]}
          testID={`timeline-node-${transition.to}`}
        />
        {!isLast && (
          <Animated.View
            style={[
              styles.segment,
              { backgroundColor: color, opacity: 0 },
              segmentStyle,
            ]}
          />
        )}
      </View>

      <View style={styles.content}>
        <View style={styles.headline}>
          <Text variant="bodyMd">{label}</Text>
          <Text variant="bodySm" color="tertiary" tabular>
            {formatTimelineTimestamp(transition.occurredAt)}
          </Text>
        </View>
        {description !== '' && (
          <Text variant="bodySm" color="tertiary">
            {description}
          </Text>
        )}
      </View>
    </View>
  );
}

const HALO_SIZE = 32;

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: space[3] },
  rail: { alignItems: 'center', width: CURRENT_NODE_SIZE },
  halo: {
    position: 'absolute',
    top: -(HALO_SIZE - CURRENT_NODE_SIZE) / 2,
    width: HALO_SIZE,
    height: HALO_SIZE,
  },
  segment: { width: SEGMENT_WIDTH, height: SEGMENT_HEIGHT, marginVertical: space[1] },
  content: { flex: 1, paddingBottom: space[5], gap: space[1] },
  headline: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
});
