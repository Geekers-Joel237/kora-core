import { useCallback } from 'react';
import {
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withSpring,
  type SharedValue,
} from 'react-native-reanimated';

import { spring, useReduceMotion } from '@/theme';

/**
 * Secousse horizontale — `docs/03-motion-and-feel.md` §6.3.
 *
 * **L'amplitude est décroissante**, jamais constante : `0 → −10 → 10 → −6 → 6 → 0`.
 * C'est ce qui la rend crédible. Une secousse à amplitude constante se lit
 * comme un bug d'animation, pas comme un refus.
 */
export const SHAKE_SEQUENCE = [-10, 10, -6, 6, 0] as const;

export interface Shake {
  style: { transform: { translateX: number }[] };
  trigger: () => void;
  offset: SharedValue<number>;
}

export function useShake() {
  const offset = useSharedValue(0);
  const reduceMotion = useReduceMotion();

  const trigger = useCallback(() => {
    if (reduceMotion) return;
    offset.value = withSequence(
      ...SHAKE_SEQUENCE.map((amplitude) => withSpring(amplitude, spring.snappy)),
    );
  }, [offset, reduceMotion]);

  const style = useAnimatedStyle(() => ({
    transform: [{ translateX: offset.value }],
  }));

  return { style, trigger, offset };
}
