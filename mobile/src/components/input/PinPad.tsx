import { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import * as ScreenCapture from 'expo-screen-capture';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { Keypad } from '@/components/money/Keypad';
import { useShake } from '@/components/money/useShake';
import { Spacer, Text } from '@/components/primitives';
import { haptic } from '@/lib/haptics';
import { radius, space, spring, timing, useReduceMotion, useTheme } from '@/theme';

export interface PinPadProps {
  title: string;
  subtitle?: string;
  length?: number;
  onComplete: (pin: string) => void;
  /** Non nul → secousse, `haptic.error`, réinitialisation en cascade. */
  error?: string | null;
  loading?: boolean;
  biometric?: { onPress: () => void; label: string } | null;
}

const DOT_SIZE = 14;
const DEFAULT_LENGTH = 4;
/** Réinitialisation en cascade après erreur, de droite à gauche. §6.3 */
const RESET_STAGGER_MS = 30;

/**
 * Composant le plus critique de l'application : **chaque opération monétaire y
 * passe** (contrat §6 — le PIN accompagne chaque requête de paiement).
 *
 * Contraintes de sécurité, `docs/06-architecture.md` §6 :
 *  - La valeur vit dans un `useRef`, jamais dans un état persisté.
 *  - La capture d'écran est bloquée tant que le composant est monté (NFR-44).
 *  - Le PIN n'est jamais affiché en clair — uniquement des pastilles (NFR-47).
 */
export function PinPad({
  title,
  subtitle,
  length = DEFAULT_LENGTH,
  onComplete,
  error,
  loading = false,
  biometric = null,
}: PinPadProps) {
  const theme = useTheme();
  const shake = useShake();

  // NFR-41 — la valeur ne quitte jamais la mémoire volatile. `filled` ne sert
  // qu'à l'affichage : il ne porte aucun chiffre.
  const pin = useRef('');
  const [filled, setFilled] = useState(0);

  useEffect(() => {
    void ScreenCapture.preventScreenCaptureAsync();
    return () => {
      void ScreenCapture.allowScreenCaptureAsync();
    };
  }, []);

  const clear = useCallback(() => {
    pin.current = '';
    setFilled(0);
  }, []);

  // Une erreur vide le PIN et secoue les pastilles.
  useEffect(() => {
    if (!error) return;
    haptic.error();
    shake.trigger();
    const timer = setTimeout(clear, RESET_STAGGER_MS * length);
    return () => clearTimeout(timer);
  }, [error, shake, clear, length]);

  const handleKey = useCallback(
    (key: string) => {
      if (loading || pin.current.length >= length) return;

      pin.current += key;
      const next = pin.current.length;
      setFilled(next);

      if (next === length) {
        haptic.press();
        onComplete(pin.current);
      }
    },
    [loading, length, onComplete],
  );

  const handleDelete = useCallback(() => {
    if (loading || pin.current.length === 0) return;
    pin.current = pin.current.slice(0, -1);
    setFilled(pin.current.length);
  }, [loading]);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text variant="titleMd" align="center">
          {title}
        </Text>
        {subtitle && (
          <>
            <Spacer size={2} />
            <Text variant="bodyMd" color="secondary" align="center">
              {subtitle}
            </Text>
          </>
        )}

        <Spacer size={8} />

        <Animated.View style={[styles.dots, shake.style]} testID="pin-dots">
          {Array.from({ length }, (_, index) => (
            <Dot
              key={index}
              filled={index < filled}
              complete={filled === length}
              errored={Boolean(error)}
              loading={loading}
            />
          ))}
        </Animated.View>

        {error && (
          <>
            <Spacer size={4} />
            <Text variant="bodySm" tint={theme.status.failed.fg} align="center">
              {error}
            </Text>
          </>
        )}
      </View>

      <Keypad
        onKey={handleKey}
        onDelete={handleDelete}
        onDeleteAll={clear}
        biometric={biometric}
        disabled={loading}
      />
    </View>
  );
}

/** Pastille de PIN — remplissage, pouls à la complétion, coloration d'erreur. §6.3 */
function Dot({
  filled,
  complete,
  errored,
  loading,
}: {
  filled: boolean;
  complete: boolean;
  errored: boolean;
  loading: boolean;
}) {
  const theme = useTheme();
  const reduceMotion = useReduceMotion();
  const scale = useSharedValue(filled ? 1 : 0.001);

  useEffect(() => {
    if (reduceMotion) {
      scale.value = filled ? 1 : 0.001;
      return;
    }

    if (loading) {
      // Le pavé se désactive et les pastilles pulsent doucement.
      scale.value = withRepeat(
        withSequence(
          withTiming(1.15, { duration: timing.slow }),
          withTiming(1, { duration: timing.slow }),
        ),
        -1,
        false,
      );
      return;
    }

    if (!filled) {
      scale.value = withSpring(0.001, spring.snappy);
      return;
    }

    // Remplissage : 0 → 1,25 → 1, expressif.
    scale.value = withSequence(
      withSpring(1.25, spring.bouncy),
      withSpring(complete ? 1.08 : 1, spring.bouncy),
      withSpring(1, spring.snappy),
    );
  }, [filled, complete, loading, reduceMotion, scale]);

  const animatedStyle = useAnimatedStyle(() => ({ transform: [{ scale: scale.value }] }));

  const color = errored
    ? theme.status.failed.fg
    : filled
      ? theme.accent.primary
      : theme.text.disabled;

  return (
    <View style={styles.dotSlot}>
      {/* Le contour vide reste visible : il indique la longueur attendue. */}
      <View style={[styles.dot, { backgroundColor: theme.text.disabled, opacity: 0.35 }]} />
      <Animated.View
        style={[styles.dot, styles.dotOverlay, { backgroundColor: color }, animatedStyle]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'space-between' },
  header: { alignItems: 'center', paddingTop: space[10], paddingHorizontal: space[5] },
  dots: { flexDirection: 'row', gap: space[4] },
  dotSlot: { width: DOT_SIZE, height: DOT_SIZE },
  dot: { width: DOT_SIZE, height: DOT_SIZE, borderRadius: radius.full },
  dotOverlay: { position: 'absolute', top: 0, left: 0 },
});
