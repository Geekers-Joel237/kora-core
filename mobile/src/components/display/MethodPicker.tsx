import { StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';

import { Icon, Pressable, Text } from '@/components/primitives';
import { PAYMENT_METHODS } from '@/features/shared/labels';
import { radius, space, spring, stroke, useTheme } from '@/theme';

export interface MethodPickerProps {
  value: string | null;
  onChange: (method: string) => void;
}

const BADGE = 40;

/**
 * Sélection de l'opérateur Mobile Money.
 *
 * La liste est **figée côté application** : `paymentMethod` est une chaîne
 * libre non validée par le backend (contrat §4). La rendre configurable par
 * l'utilisateur permettrait d'envoyer n'importe quoi.
 *
 * Les couleurs de marque n'apparaissent que dans la pastille de 40 dp —
 * design system §2.6. Elles ne teintent jamais un fond ni un texte.
 */
export function MethodPicker({ value, onChange }: MethodPickerProps) {
  const theme = useTheme();

  return (
    <View style={styles.list}>
      {PAYMENT_METHODS.map((method) => {
        const selected = value === method.id;
        return (
          <Pressable
            key={method.id}
            onPress={() => onChange(method.id)}
            haptic="select"
            scale="card"
            accessibilityRole="checkbox"
            accessibilityState={{ checked: selected }}
            accessibilityLabel={method.label}
            testID={`method-${method.id}`}
            style={[
              styles.row,
              {
                backgroundColor: selected ? theme.accent.wash : theme.bg.surface1,
                borderRadius: radius.md,
                borderWidth: selected ? stroke.thin : stroke.hairline,
                borderColor: selected ? theme.accent.primary : theme.overlay.hairline,
              },
            ]}
          >
            <View style={[styles.badge, { backgroundColor: method.brand }]} />
            <Text variant="bodyMd" style={styles.label}>
              {method.label}
            </Text>
            <Checkmark visible={selected} color={theme.accent.primary} />
          </Pressable>
        );
      })}
    </View>
  );
}

/** La coche entre en échelle `0 → 1` en `bouncy` — `docs/04-components.md` §5. */
function Checkmark({ visible, color }: { visible: boolean; color: string }) {
  const scale = useSharedValue(visible ? 1 : 0);
  scale.value = withSpring(visible ? 1 : 0, spring.bouncy);

  const animatedStyle = useAnimatedStyle(() => ({ transform: [{ scale: scale.value }] }));

  return (
    <Animated.View style={animatedStyle}>
      <Icon name="check-circle" size="sm" color={color} />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  list: { gap: space[3] },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    paddingHorizontal: space[4],
    paddingVertical: space[4],
  },
  badge: { width: BADGE, height: BADGE, borderRadius: radius.full },
  label: { flex: 1 },
});