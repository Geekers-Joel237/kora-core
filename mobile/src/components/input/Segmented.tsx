import { useCallback, useState } from 'react';
import { StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import Animated, { useAnimatedStyle, withSpring, withTiming } from 'react-native-reanimated';

import { Pressable, Text } from '@/components/primitives';
import { radius, space, spring, timing, useReduceMotion, useTheme } from '@/theme';

export interface SegmentedOption<T extends string> {
  value: T | null;
  label: string;
}

export interface SegmentedProps<T extends string> {
  options: SegmentedOption<T>[];
  value: T | null;
  onChange: (value: T | null) => void;
  accessibilityLabel?: string;
  testID?: string;
}

const TRACK_PADDING = 3;

/**
 * Contrôle segmenté — `docs/04-components.md`, filtres de `05-screens.md` §5.
 *
 * L'indicateur **glisse** d'un segment à l'autre plutôt que d'apparaître à sa
 * nouvelle place : c'est ce déplacement qui rend le changement de sélection
 * lisible sans avoir à relire les libellés.
 *
 * La largeur des segments est mesurée, jamais calculée : des libellés de
 * longueurs différentes ne se répartissent pas à parts égales, et un indicateur
 * positionné par division tomberait à côté.
 */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
  accessibilityLabel,
  testID,
}: SegmentedProps<T>) {
  const theme = useTheme();
  const reduceMotion = useReduceMotion();

  const [widths, setWidths] = useState<number[]>(() => options.map(() => 0));

  const selectedIndex = Math.max(
    0,
    options.findIndex((option) => option.value === value),
  );

  const measure = useCallback(
    (index: number) => (event: LayoutChangeEvent) => {
      const measured = event.nativeEvent.layout.width;
      setWidths((current) => {
        if (current[index] === measured) return current;
        const next = [...current];
        next[index] = measured;
        return next;
      });
    },
    [],
  );

  // Position dérivée de la mesure, calculée pendant le rendu. Aucune valeur
  // partagée n'est écrite ici : Reanimated interdit l'écriture d'un
  // `SharedValue` pendant un rendu, et l'animation se déclare directement dans
  // le style animé, qui referme sur ces deux nombres.
  const targetOffset = widths.slice(0, selectedIndex).reduce((sum, w) => sum + w, 0);
  const targetWidth = widths[selectedIndex] ?? 0;

  const indicatorStyle = useAnimatedStyle(() => {
    const animate = (to: number) =>
      reduceMotion ? withTiming(to, { duration: timing.fast }) : withSpring(to, spring.snappy);
    return {
      transform: [{ translateX: animate(targetOffset) }],
      width: animate(targetWidth),
    };
  }, [targetOffset, targetWidth, reduceMotion]);

  return (
    <View
      style={[
        styles.track,
        { backgroundColor: theme.bg.surface2, borderRadius: radius.md, padding: TRACK_PADDING },
      ]}
      accessibilityRole="tablist"
      {...(accessibilityLabel !== undefined && { accessibilityLabel })}
      {...(testID !== undefined && { testID })}
    >
      {targetWidth > 0 && (
        <Animated.View
          style={[
            styles.indicator,
            { backgroundColor: theme.bg.surface3, borderRadius: radius.sm, top: TRACK_PADDING, bottom: TRACK_PADDING },
            indicatorStyle,
          ]}
        />
      )}

      {options.map((option, index) => {
        const selected = option.value === value;
        return (
          <View key={option.value ?? '·'} style={styles.segment} onLayout={measure(index)}>
            <Pressable
              onPress={() => onChange(option.value)}
              haptic="select"
              scale="card"
              accessibilityRole="tab"
              accessibilityState={{ selected }}
              accessibilityLabel={option.label}
              testID={testID ? `${testID}-${option.value ?? 'all'}` : undefined}
              style={styles.label}
            >
              <Text variant="labelMd" color={selected ? 'primary' : 'tertiary'} numberOfLines={1}>
                {option.label}
              </Text>
            </Pressable>
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  track: { flexDirection: 'row', alignItems: 'stretch' },
  indicator: { position: 'absolute', left: TRACK_PADDING },
  segment: { flex: 1 },
  label: { paddingVertical: space[2], alignItems: 'center', justifyContent: 'center' },
});