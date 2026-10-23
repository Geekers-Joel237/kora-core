import { useMemo } from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { triggerHaptic, type HapticStyle } from '@/lib/haptics';
import { layout, pressScale, spring, useReduceMotion, type PressScaleToken } from '@/theme';

export interface PressableProps {
  onPress?: () => void;
  onLongPress?: () => void;
  /** Défaut `'press'`. `'none'` est un choix explicite, pas un oubli. §3 */
  haptic?: HapticStyle;
  /** Facteur de compression. Un grand élément se comprime **moins**. §4 */
  scale?: PressScaleToken;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  accessibilityLabel?: string;
  accessibilityRole?: 'button' | 'link' | 'tab' | 'checkbox' | 'switch';
  accessibilityState?: { disabled?: boolean; selected?: boolean; checked?: boolean };
  testID?: string;
  children: React.ReactNode;
}

/** Opacité au toucher. Discrète : la compression porte l'essentiel du signal. */
const PRESSED_OPACITY = 0.9;
const OPACITY_IN_MS = 80;
const OPACITY_OUT_MS = 120;
const LONG_PRESS_MS = 500;

/**
 * Socle de toute interaction de l'application.
 *
 * Trois exigences non négociables — `docs/03-motion-and-feel.md` §4 :
 *
 * 1. **Le geste passe par `Gesture.Tap()` de gesture-handler**, jamais par un
 *    `Touchable*` de React Native. Ces derniers passent par le pont JS : sous
 *    charge, le retour arrive avec 200 ms de retard, ce qui est immédiatement
 *    perceptible. Ici, la compression et l'opacité vivent sur le thread UI et
 *    répondent même si JavaScript est bloqué (Loi 3).
 *
 * 2. **Le relâchement rebondit vers 1**, il ne revient pas linéairement. C'est
 *    ce léger dépassement qui donne la sensation de matière.
 *
 * 3. **La zone tactile est étendue à 44 dp**, indépendamment de la taille
 *    visuelle (NFR-51).
 */
export function Pressable({
  onPress,
  onLongPress,
  haptic = 'press',
  scale = 'button',
  disabled = false,
  style,
  accessibilityLabel,
  accessibilityRole = 'button',
  accessibilityState,
  testID,
  children,
}: PressableProps) {
  const reduceMotion = useReduceMotion();
  const progress = useSharedValue(0);

  const target = pressScale[scale];

  const gesture = useMemo(() => {
    const tap = Gesture.Tap()
      // Sans cela, gesture-handler annule le geste au bout de ~500 ms et le
      // retour visuel se rétracte alors que le doigt est toujours posé.
      .maxDuration(10_000)
      .enabled(!disabled)
      .onBegin(() => {
        'worklet';
        progress.value = 1;
        if (haptic !== 'none') runOnJS(triggerHaptic)(haptic);
      })
      .onFinalize(() => {
        'worklet';
        progress.value = 0;
      })
      .onEnd((_event, success) => {
        'worklet';
        if (success && onPress) runOnJS(onPress)();
      });

    if (!onLongPress) return tap;

    const long = Gesture.LongPress()
      .minDuration(LONG_PRESS_MS)
      .enabled(!disabled)
      .onStart(() => {
        'worklet';
        runOnJS(triggerHaptic)('commit');
        runOnJS(onLongPress)();
      });

    return Gesture.Simultaneous(tap, long);
  }, [disabled, haptic, onPress, onLongPress, progress]);

  const animatedStyle = useAnimatedStyle(() => {
    // §7.2 — « réduire les animations » remplace le déplacement par un fondu.
    // Le retour visuel ne disparaît jamais : il change de forme.
    if (reduceMotion) {
      return {
        opacity: withTiming(progress.value === 1 ? PRESSED_OPACITY : 1, {
          duration: OPACITY_IN_MS,
        }),
      };
    }

    return {
      transform: [{ scale: withSpring(progress.value === 1 ? target : 1, spring.snappy) }],
      opacity: withTiming(progress.value === 1 ? PRESSED_OPACITY : 1, {
        duration: progress.value === 1 ? OPACITY_IN_MS : OPACITY_OUT_MS,
      }),
    };
  }, [reduceMotion, target]);

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View
        style={[animatedStyle, disabled && styles.disabled, style]}
        hitSlop={HIT_SLOP}
        accessible
        accessibilityRole={accessibilityRole}
        {...(accessibilityLabel !== undefined && { accessibilityLabel })}
        accessibilityState={{ disabled, ...accessibilityState }}
        {...(testID !== undefined && { testID })}
      >
        {children}
      </Animated.View>
    </GestureDetector>
  );
}

/** Étend la zone tactile à la cible minimale de 44 dp. NFR-51 */
const HIT_SLOP = {
  top: layout.minTouchTarget / 4,
  bottom: layout.minTouchTarget / 4,
  left: layout.minTouchTarget / 4,
  right: layout.minTouchTarget / 4,
};

const styles = { disabled: { opacity: 0.4 } } as const;
