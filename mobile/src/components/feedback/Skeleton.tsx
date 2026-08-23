import { useEffect, useState } from 'react';
import { StyleSheet, View, type DimensionValue, type StyleProp, type ViewStyle } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

import { radius as radiusScale, SKELETON_DELAY_MS, useReduceMotion, useTheme } from '@/theme';
import type { RadiusToken } from '@/theme';

const SHIMMER_CYCLE_MS = 1200;
/** Le miroitement balaie 40 % de la largeur du conteneur. §6.5 */
const SHIMMER_WIDTH_RATIO = 0.4;
const SHIMMER_PEAK_OPACITY = 0.08;

export interface SkeletonProps {
  width?: DimensionValue;
  height: number;
  radius?: RadiusToken;
  style?: StyleProp<ViewStyle>;
}

/**
 * Bloc de chargement. **Aucun indicateur circulaire centré n'existe dans
 * l'application** — §6.5, sans exception.
 *
 * Un squelette reproduit la forme exacte du contenu final : c'est ce qui rend
 * l'attente lisible plutôt que suspendue.
 */
export function Skeleton({ width = '100%', height, radius = 'sm', style }: SkeletonProps) {
  const theme = useTheme();
  const reduceMotion = useReduceMotion();
  const progress = useSharedValue(0);
  const [measuredWidth, setMeasuredWidth] = useState(0);

  useEffect(() => {
    if (reduceMotion) return;
    progress.value = withRepeat(withTiming(1, { duration: SHIMMER_CYCLE_MS }), -1, false);
  }, [progress, reduceMotion]);

  const shimmerWidth = measuredWidth * SHIMMER_WIDTH_RATIO;

  const shimmerStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: -shimmerWidth + progress.value * (measuredWidth + shimmerWidth) },
    ],
    opacity:
      progress.value < 0.5
        ? progress.value * 2 * SHIMMER_PEAK_OPACITY
        : (1 - progress.value) * 2 * SHIMMER_PEAK_OPACITY,
  }));

  return (
    <View
      onLayout={(event) => setMeasuredWidth(event.nativeEvent.layout.width)}
      style={[
        {
          width,
          height,
          borderRadius: radiusScale[radius],
          backgroundColor: theme.bg.surface2,
          overflow: 'hidden',
        },
        style,
      ]}
    >
      {!reduceMotion && measuredWidth > 0 && (
        <Animated.View
          style={[
            styles.shimmer,
            { width: shimmerWidth, backgroundColor: theme.text.primary },
            shimmerStyle,
          ]}
        />
      )}
    </View>
  );
}

/**
 * Retarde l'apparition d'un état de chargement.
 *
 * NFR-07 — **aucun état de chargement ne s'affiche sous 200 ms.** En dessous,
 * un squelette qui apparaît puis disparaît aussitôt se lit comme un
 * clignotement : l'attente perçue devient pire que l'attente réelle.
 *
 * @returns `true` seulement si le chargement dure au-delà du seuil.
 */
export function useDelayedLoading(loading: boolean, delayMs = SKELETON_DELAY_MS): boolean {
  const [elapsed, setElapsed] = useState(false);

  useEffect(() => {
    if (!loading) return;
    const timer = setTimeout(() => setElapsed(true), delayMs);
    return () => {
      clearTimeout(timer);
      setElapsed(false);
    };
  }, [loading, delayMs]);

  // Dérivé plutôt que stocké : la fin du chargement masque le squelette
  // immédiatement, sans passer par un second rendu.
  return loading && elapsed;
}

const styles = StyleSheet.create({
  shimmer: { position: 'absolute', top: 0, bottom: 0 },
});
