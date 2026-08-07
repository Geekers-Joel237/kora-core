import { useEffect } from 'react';
import { Modal, StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { Button } from '@/components/action/Button';
import { Text } from '@/components/primitives';
import { radius, space, spring, timing, useReduceMotion, useTheme } from '@/theme';

export interface DialogProps {
  visible: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  onConfirm: () => void;
  cancelLabel?: string;
  onCancel: () => void;
  /** Une action destructive prend la variante `danger`. */
  destructive?: boolean;
}

const DIALOG_MAX_WIDTH = 340;
const ENTER_SCALE = 0.92;

/**
 * Boîte de dialogue de confirmation.
 *
 * Réservée aux décisions **bloquantes et réversibles seulement par l'utilisateur** :
 * déconnexion, abandon d'un parcours en cours. Une erreur réseau ne mérite
 * jamais une modale (NFR-30) ; un résultat d'opération monétaire mérite un
 * écran plein, pas un dialogue.
 */
export function Dialog({
  visible,
  title,
  message,
  confirmLabel,
  onConfirm,
  cancelLabel = 'Annuler',
  onCancel,
  destructive = false,
}: DialogProps) {
  const theme = useTheme();
  const reduceMotion = useReduceMotion();
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = visible ? 1 : 0;
  }, [visible, progress]);

  const cardStyle = useAnimatedStyle(() => {
    const opacity = withTiming(progress.value, { duration: timing.fast });
    if (reduceMotion) return { opacity };
    return {
      opacity,
      transform: [
        {
          scale: withSpring(
            progress.value === 1 ? 1 : ENTER_SCALE,
            spring.standard,
          ),
        },
      ],
    };
  }, [reduceMotion]);

  return (
    <Modal visible={visible} transparent animationType="none" onRequestClose={onCancel}>
      <View style={[styles.backdrop, { backgroundColor: theme.overlay.scrim }]}>
        <Animated.View
          style={[
            styles.card,
            {
              backgroundColor: theme.bg.surface2,
              borderRadius: radius.xl,
              padding: space[6],
            },
            cardStyle,
          ]}
        >
          <Text variant="titleMd" align="center">
            {title}
          </Text>
          <View style={styles.message}>
            <Text variant="bodyMd" color="secondary" align="center">
              {message}
            </Text>
          </View>
          <View style={styles.actions}>
            <Button
              label={confirmLabel}
              onPress={onConfirm}
              variant={destructive ? 'danger' : 'primary'}
              size="md"
            />
            <Button label={cancelLabel} onPress={onCancel} variant="ghost" size="md" />
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: space[6] },
  card: { width: '100%', maxWidth: DIALOG_MAX_WIDTH },
  message: { marginTop: space[2] },
  actions: { marginTop: space[6], gap: space[2] },
});
