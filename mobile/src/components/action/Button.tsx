import { useEffect } from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

import { Icon, Pressable, Text, type IconName } from '@/components/primitives';
import {
  layout,
  radius,
  space,
  timing,
  useReduceMotion,
  useTheme,
  type Theme,
} from '@/theme';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'lg' | 'md' | 'sm';

export interface ButtonProps {
  label: string;
  onPress: () => void;
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  disabled?: boolean;
  icon?: IconName;
  fullWidth?: boolean;
  style?: StyleProp<ViewStyle>;
  testID?: string;
}

function colorsFor(theme: Theme, variant: ButtonVariant) {
  switch (variant) {
    case 'primary':
      return { bg: theme.accent.primary, fg: theme.text.onAccent };
    case 'secondary':
      return { bg: theme.bg.surface2, fg: theme.text.primary };
    case 'ghost':
      return { bg: 'transparent', fg: theme.accent.primary };
    case 'danger':
      return { bg: theme.status.failed.bg, fg: theme.status.failed.fg };
  }
}

const LABEL_VARIANT = { lg: 'bodyLg', md: 'bodyMd', sm: 'labelMd' } as const;

/**
 * Bouton de l'application.
 *
 * **Le chargement ne change pas la largeur.** Un bouton qui rétrécit pendant sa
 * requête fait sauter la mise en page sous le doigt de l'utilisateur — défaut
 * courant et immédiatement perceptible. Le libellé sort en fondu, trois points
 * pulsent à sa place, et le conteneur ne bouge pas d'un pixel.
 */
export function Button({
  label,
  onPress,
  variant = 'primary',
  size = 'lg',
  loading = false,
  disabled = false,
  icon,
  fullWidth = true,
  style,
  testID,
}: ButtonProps) {
  const theme = useTheme();
  const { bg, fg } = colorsFor(theme, variant);
  const inactive = disabled || loading;

  return (
    <Pressable
      onPress={onPress}
      disabled={inactive}
      scale="button"
      haptic={variant === 'ghost' ? 'tap' : 'press'}
      accessibilityLabel={label}
      accessibilityState={{ disabled: inactive }}
      {...(testID !== undefined && { testID })}
      style={[
        styles.base,
        {
          // `minHeight`, pas `height` : à 200 % de taille de police le
          // libellé passe sur deux lignes et doit faire grandir le bouton,
          // jamais être rogné — NFR d'accessibilité, `08-quality-bar.md`.
          minHeight: layout.buttonHeight[size],
          backgroundColor: bg,
          borderRadius: radius.md,
          alignSelf: fullWidth ? 'stretch' : 'flex-start',
          paddingHorizontal: fullWidth ? space[4] : space[5],
        },
        style,
      ]}
    >
      {/* Le libellé reste monté et occupe sa largeur, même invisible : c'est ce
          qui empêche le bouton de se rétracter pendant le chargement. */}
      <View style={[styles.content, loading && styles.hidden]}>
        {icon && <Icon name={icon} size="sm" color={fg} />}
        <Text variant={LABEL_VARIANT[size]} tint={fg}>
          {label}
        </Text>
      </View>

      {loading && (
        <View style={styles.overlay} pointerEvents="none">
          <LoadingDots color={fg} />
        </View>
      )}
    </Pressable>
  );
}

const DOT_COUNT = 3;
const DOT_STAGGER_MS = 150;
const DOT_SIZE = 6;

/** Trois points en cascade. Jamais un indicateur circulaire — §6.5. */
function LoadingDots({ color }: { color: string }) {
  return (
    <View style={styles.dots}>
      {Array.from({ length: DOT_COUNT }, (_, index) => (
        <Dot key={index} color={color} delayMs={index * DOT_STAGGER_MS} />
      ))}
    </View>
  );
}

function Dot({ color, delayMs }: { color: string; delayMs: number }) {
  const opacity = useSharedValue(0.3);
  const reduceMotion = useReduceMotion();

  useEffect(() => {
    if (reduceMotion) {
      opacity.value = 0.6;
      return;
    }
    opacity.value = withRepeat(
      withSequence(
        withTiming(0.3, { duration: delayMs }),
        withTiming(1, { duration: timing.fast }),
        withTiming(0.3, { duration: timing.fast }),
      ),
      -1,
      false,
    );
  }, [delayMs, opacity, reduceMotion]);

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
  base: { alignItems: 'center', justifyContent: 'center' },
  content: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
  hidden: { opacity: 0 },
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dots: { flexDirection: 'row', gap: space[1] },
});
