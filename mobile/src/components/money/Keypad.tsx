import { StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { Icon, Pressable, Text } from '@/components/primitives';
import { radius, space, useTheme } from '@/theme';

export type KeypadKey = string;

export interface KeypadProps {
  onKey: (key: KeypadKey) => void;
  onDelete: () => void;
  onDeleteAll?: () => void;
  /** Touche de gauche du dernier rang. `'000'` pour les montants, rien sinon. */
  leadingKey?: KeypadKey | null;
  /** Remplace la touche de gauche par la biométrie sur le pavé PIN. */
  biometric?: { onPress: () => void; label: string } | null;
  disabled?: boolean;
}

/** Hauteur de touche — `docs/04-components.md` §3. */
const KEY_HEIGHT = 72;
const DIGITS = ['1', '2', '3', '4', '5', '6', '7', '8', '9'] as const;

/**
 * Grille 3×4 partagée par `AmountKeypad` et `PinPad`.
 *
 * **Aucun `TextInput` n'est utilisé pour saisir un montant ou un PIN** : le
 * clavier système impose sa propre disposition, son propre timing et sa propre
 * haptique, dont aucun n'est contrôlable. Le pavé maison est le seul moyen de
 * tenir la promesse du §6.2 et du §6.3.
 */
export function Keypad({
  onKey,
  onDelete,
  onDeleteAll,
  leadingKey = null,
  biometric = null,
  disabled = false,
}: KeypadProps) {
  const { t } = useTranslation();
  const theme = useTheme();

  const renderKey = (label: string, onPress: () => void, testID: string) => (
    <Pressable
      key={testID}
      onPress={onPress}
      disabled={disabled}
      scale="key"
      haptic="tap"
      accessibilityLabel={label}
      testID={testID}
      style={[styles.key, { borderRadius: radius.md }]}
    >
      <Text variant="titleLg" tabular>
        {label}
      </Text>
    </Pressable>
  );

  return (
    <View style={styles.grid}>
      {DIGITS.map((digit) => renderKey(digit, () => onKey(digit), `key-${digit}`))}

      {biometric ? (
        <Pressable
          onPress={biometric.onPress}
          disabled={disabled}
          scale="key"
          haptic="press"
          accessibilityLabel={biometric.label}
          testID="key-biometric"
          style={[styles.key, { borderRadius: radius.md }]}
        >
          <Icon name="fingerprint" size="lg" color={theme.accent.primary} />
        </Pressable>
      ) : leadingKey ? (
        renderKey(leadingKey, () => onKey(leadingKey), `key-${leadingKey}`)
      ) : (
        <View style={styles.key} />
      )}

      {renderKey('0', () => onKey('0'), 'key-0')}

      <Pressable
        onPress={onDelete}
        {...(onDeleteAll && { onLongPress: onDeleteAll })}
        disabled={disabled}
        scale="key"
        haptic="tap"
        accessibilityLabel={t('feedback.clear')}
        testID="key-delete"
        style={[styles.key, { borderRadius: radius.md }]}
      >
        <Icon name="close" size="md" color={theme.text.secondary} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  key: {
    width: '33.333%',
    height: KEY_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: space[2],
  },
});
