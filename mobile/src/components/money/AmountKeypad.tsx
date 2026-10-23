import { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { FadeInDown, FadeOutDown } from 'react-native-reanimated';

import { Pressable, Spacer, Surface, Text } from '@/components/primitives';
import { haptic } from '@/lib/haptics';
import { formatMinorToString } from '@/lib/money';
import { radius, space, useTheme } from '@/theme';
import { Amount } from './Amount';
import { Keypad } from './Keypad';
import { useShake } from './useShake';

export interface AmountKeypadProps {
  currency: string;
  /**
   * Plafond en unité mineure. Dépôt : aucun. Retrait et transfert : le solde
   * disponible — le dépassement est signalé **avant** soumission.
   */
  maxMinor?: number;
  /** Affiché sous le montant : « Solde après opération ». */
  remainingLabel?: string;
  quickAmounts?: number[];
  onChange: (minor: number) => void;
  value: number;
}

/** Au-delà de ce nombre de chiffres, le montant passe à une taille inférieure. §6.2 */
const SIZE_BREAKPOINT = 7;
const MAX_DIGITS = 12;

/**
 * Pavé de saisie de montant plein écran — `docs/04-components.md` §3.
 *
 * Le montant est un **entier d'unité mineure** du début à la fin : aucune
 * chaîne intermédiaire, aucune virgule flottante. Pour le XOF, chaque chiffre
 * saisi est un franc.
 */
export function AmountKeypad({
  currency,
  maxMinor,
  remainingLabel,
  quickAmounts = [],
  onChange,
  value,
}: AmountKeypadProps) {
  const theme = useTheme();
  const shake = useShake();
  const [overflow, setOverflow] = useState(false);

  const reject = useCallback(() => {
    setOverflow(true);
    shake.trigger();
    haptic.error();
    // Le montant revient à sa couleur normale après la secousse.
    setTimeout(() => setOverflow(false), 400);
  }, [shake]);

  const apply = useCallback(
    (next: number) => {
      if (maxMinor !== undefined && next > maxMinor) {
        reject();
        return;
      }
      onChange(next);
    },
    [maxMinor, onChange, reject],
  );

  const handleKey = useCallback(
    (key: string) => {
      const digits = `${value === 0 ? '' : value}${key}`;
      if (digits.length > MAX_DIGITS) return;
      apply(Number(digits));
    },
    [value, apply],
  );

  const handleDelete = useCallback(() => {
    const digits = String(value).slice(0, -1);
    onChange(digits === '' ? 0 : Number(digits));
  }, [value, onChange]);

  const size = String(value).length > SIZE_BREAKPOINT ? 'displayMd' : 'displayLg';

  return (
    <View style={styles.container}>
      <View style={styles.display}>
        <Animated.View style={shake.style}>
          <Amount
            minor={value}
            currency={currency}
            size={size}
            align="center"
            tint={overflow ? theme.status.failed.fg : theme.text.primary}
            testID="amount-display"
          />
        </Animated.View>

        {remainingLabel && (
          <>
            <Spacer size={2} />
            <Text variant="bodySm" color="tertiary" align="center">
              {remainingLabel}
            </Text>
          </>
        )}
      </View>

      {quickAmounts.length > 0 && (
        <View style={styles.quick}>
          {quickAmounts.map((amount) => (
            <Pressable
              key={amount}
              onPress={() => apply(amount)}
              haptic="select"
              scale="card"
              accessibilityLabel={`Montant ${formatMinorToString(amount, currency)}`}
              testID={`quick-${amount}`}
            >
              <Surface elevation={2} radius="full" padding={2} style={styles.quickChip}>
                <Text variant="labelMd" color="secondary">
                  {formatMinorToString(amount, currency)}
                </Text>
              </Surface>
            </Pressable>
          ))}
        </View>
      )}

      <Keypad onKey={handleKey} onDelete={handleDelete} onDeleteAll={() => onChange(0)} leadingKey="000" />
    </View>
  );
}

/**
 * Entrée d'un chiffre par le bas — §6.2.
 * Exporté séparément : les écrans qui composent leur propre affichage de
 * montant réutilisent la même animation d'entrée.
 */
export const digitEnter = FadeInDown.springify().damping(26).stiffness(340);
export const digitExit = FadeOutDown.duration(150);

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'flex-end' },
  display: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  quick: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: space[2],
    marginBottom: space[4],
  },
  quickChip: { paddingHorizontal: space[4], borderRadius: radius.full },
});
