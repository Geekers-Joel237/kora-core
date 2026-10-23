import { StyleSheet } from 'react-native';
import Animated, { useAnimatedStyle, withSpring, withTiming } from 'react-native-reanimated';

import { Pressable } from '@/components/primitives';
import { radius, spring, timing, useReduceMotion, useTheme } from '@/theme';

export interface ToggleProps {
  value: boolean;
  onChange: (value: boolean) => void;
  accessibilityLabel: string;
  disabled?: boolean;
  testID?: string;
}

const TRACK_WIDTH = 51;
const TRACK_HEIGHT = 31;
const KNOB_SIZE = 27;
const KNOB_INSET = 2;

/**
 * Interrupteur du design system.
 *
 * Écrit plutôt que repris du `Switch` de React Native : celui-ci se peint aux
 * couleurs du système sur Android et ignore les jetons du §2. Un réglage qui
 * ne ressemble pas au reste de l'application saute aux yeux précisément là où
 * l'utilisateur compare des lignes entre elles.
 *
 * Le pouce glisse en ressort ; la piste change de couleur en fondu — une
 * couleur qui rebondit se lit comme un défaut de rendu.
 */
export function Toggle({
  value,
  onChange,
  accessibilityLabel,
  disabled = false,
  testID,
}: ToggleProps) {
  const theme = useTheme();
  const reduceMotion = useReduceMotion();

  const target = value ? TRACK_WIDTH - KNOB_SIZE - KNOB_INSET : KNOB_INSET;

  const knobStyle = useAnimatedStyle(
    () => ({
      transform: [
        {
          translateX: reduceMotion
            ? withTiming(target, { duration: timing.fast })
            : withSpring(target, spring.snappy),
        },
      ],
    }),
    [target, reduceMotion],
  );

  const trackStyle = useAnimatedStyle(
    () => ({
      backgroundColor: withTiming(value ? theme.accent.primary : theme.bg.surface3, {
        duration: timing.fast,
      }),
    }),
    [value, theme],
  );

  return (
    <Pressable
      onPress={() => onChange(!value)}
      disabled={disabled}
      haptic="select"
      scale="hero"
      accessibilityRole="switch"
      accessibilityState={{ checked: value, disabled }}
      accessibilityLabel={accessibilityLabel}
      testID={testID}
    >
      {/* Aucune opacité ajoutée ici : `Pressable` applique déjà celle de
          l'état désactivé, et la doubler rendrait l'interrupteur illisible. */}
      <Animated.View style={[styles.track, { borderRadius: radius.full }, trackStyle]}>
        <Animated.View
          style={[
            styles.knob,
            { backgroundColor: theme.text.onAccent, borderRadius: radius.full },
            knobStyle,
          ]}
        />
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  track: { width: TRACK_WIDTH, height: TRACK_HEIGHT, justifyContent: 'center' },
  knob: { width: KNOB_SIZE, height: KNOB_SIZE, position: 'absolute', left: 0 },
});
