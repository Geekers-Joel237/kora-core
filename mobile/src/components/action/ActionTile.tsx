import { StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';

import { Icon, Pressable, Spacer, Text, type IconName } from '@/components/primitives';
import { radius, space, STAGGER_MS, useReduceMotion, useTheme } from '@/theme';

export interface ActionTileProps {
  icon: IconName;
  label: string;
  onPress: () => void;
  disabled?: boolean;
  /** Rang dans la cascade d'entrée — §7.3, jamais plus de 8. */
  index?: number;
  testID?: string;
}

const BADGE_SIZE = 44;

/** Les trois actions monétaires de l'accueil — `docs/04-components.md` §6. */
export function ActionTile({
  icon,
  label,
  onPress,
  disabled = false,
  index = 0,
  testID,
}: ActionTileProps) {
  const theme = useTheme();
  const reduceMotion = useReduceMotion();

  return (
    <Animated.View
      style={styles.wrapper}
      {...(!reduceMotion && {
        entering: FadeInDown.delay(index * STAGGER_MS)
          .springify()
          .damping(20)
          .stiffness(200),
      })}
    >
      <Pressable
        onPress={onPress}
        disabled={disabled}
        scale="card"
        haptic="press"
        accessibilityLabel={label}
        {...(testID !== undefined && { testID })}
        style={[styles.tile, { backgroundColor: theme.bg.surface1, borderRadius: radius.lg }]}
      >
        <View
          style={[
            styles.badge,
            { backgroundColor: theme.accent.wash, borderRadius: radius.full },
          ]}
        >
          <Icon name={icon} size="md" color={theme.accent.primary} />
        </View>
        <Spacer size={2} />
        <Text variant="labelMd" color="secondary">
          {label}
        </Text>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrapper: { flex: 1 },
  tile: { alignItems: 'center', paddingVertical: space[4], paddingHorizontal: space[2] },
  badge: { width: BADGE_SIZE, height: BADGE_SIZE, alignItems: 'center', justifyContent: 'center' },
});
