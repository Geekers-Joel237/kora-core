import { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { useShake } from '@/components/money/useShake';
import { Text } from '@/components/primitives';
import { haptic } from '@/lib/haptics';
import { radius, space, spring, stroke, type as typeScale, useTheme } from '@/theme';

export interface OtpInputProps {
  length?: number;
  onComplete: (code: string) => void;
  error?: string | null;
  disabled?: boolean;
  autoFocus?: boolean;
}

const DEFAULT_LENGTH = 6;
const CELL_WIDTH = 48;
const CELL_HEIGHT = 56;
/** Un collage remplit les cellules en cascade de 40 ms. §4 */
const PASTE_STAGGER_MS = 40;

/**
 * Saisie du code à six chiffres reçu **par e-mail** — contrat §1.
 *
 * ⚠️ Le code n'arrivant pas par SMS, `autoComplete="sms-otp"` ne sert à rien
 * et n'est pas utilisé. L'écran appelant doit à la place proposer un bouton
 * « Ouvrir ma boîte mail ». Voir `docs/05-screens.md` §2.4.
 *
 * L'implémentation repose sur un `TextInput` invisible plein cadre : c'est le
 * seul moyen d'obtenir le collage système et la gestion du curseur sans
 * synchroniser six champs distincts, qui se disputeraient le focus.
 */
export function OtpInput({
  length = DEFAULT_LENGTH,
  onComplete,
  error,
  disabled = false,
  autoFocus = true,
}: OtpInputProps) {
  const theme = useTheme();
  const shake = useShake();
  const inputRef = useRef<TextInput>(null);
  const [code, setCode] = useState('');
  const [focused, setFocused] = useState(false);

  // Réinitialisation pendant le rendu — le motif documenté par React pour
  // ajuster un état quand une propriété change. Le faire dans un effet
  // provoquerait un rendu en cascade : les cellules s'afficheraient encore
  // remplies une image après l'erreur.
  const [seenError, setSeenError] = useState(error);
  if (error !== seenError) {
    setSeenError(error);
    if (error) setCode('');
  }

  // Les effets de bord, eux, restent dans un effet.
  useEffect(() => {
    if (!error) return;
    haptic.error();
    shake.trigger();
  }, [error, shake]);

  const handleChange = useCallback(
    (text: string) => {
      const digits = text.replace(/\D/g, '').slice(0, length);
      setCode(digits);

      if (digits.length === length) {
        haptic.press();
        // Soumission automatique, sans bouton : le code est complet, attendre
        // une action supplémentaire n'apporterait rien.
        onComplete(digits);
      }
    },
    [length, onComplete],
  );

  return (
    <View>
      <Animated.View style={[styles.row, shake.style]}>
        {Array.from({ length }, (_, index) => (
          <Cell
            key={index}
            digit={code[index] ?? ''}
            active={focused && index === code.length}
            errored={Boolean(error)}
            enterDelayMs={index * PASTE_STAGGER_MS}
          />
        ))}
      </Animated.View>

      <TextInput
        ref={inputRef}
        value={code}
        onChangeText={handleChange}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        keyboardType="number-pad"
        textContentType="oneTimeCode"
        maxLength={length}
        editable={!disabled}
        autoFocus={autoFocus}
        caretHidden
        style={styles.hiddenInput}
        accessibilityLabel={`Code de vérification à ${length} chiffres`}
        testID="otp-input"
      />

      {error && (
        <View style={styles.error}>
          <Text variant="bodySm" tint={theme.status.failed.fg} align="center">
            {error}
          </Text>
        </View>
      )}
    </View>
  );
}

function Cell({
  digit,
  active,
  errored,
  enterDelayMs,
}: {
  digit: string;
  active: boolean;
  errored: boolean;
  enterDelayMs: number;
}) {
  const theme = useTheme();
  const scale = useSharedValue(1);

  useEffect(() => {
    scale.value = withSpring(active ? 1.04 : 1, spring.snappy);
  }, [active, scale]);

  // Le décalage n'a d'effet visible que sur un collage : les cellules se
  // remplissent alors en cascade plutôt que toutes ensemble.
  useEffect(() => {
    if (!digit) return;
    const timer = setTimeout(() => {
      scale.value = withSpring(1.08, spring.bouncy);
      scale.value = withSpring(1, spring.snappy);
    }, enterDelayMs);
    return () => clearTimeout(timer);
  }, [digit, enterDelayMs, scale]);

  const animatedStyle = useAnimatedStyle(() => ({ transform: [{ scale: scale.value }] }));

  const borderColor = errored
    ? theme.status.failed.fg
    : active
      ? theme.accent.primary
      : theme.overlay.border;

  return (
    <Animated.View
      style={[
        styles.cell,
        {
          backgroundColor: theme.bg.surface2,
          borderColor,
          borderWidth: active || errored ? stroke.thick : stroke.hairline,
        },
        animatedStyle,
      ]}
    >
      <Text variant="titleMd" tabular>
        {digit}
      </Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'center', gap: space[2] },
  cell: {
    width: CELL_WIDTH,
    height: CELL_HEIGHT,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  // Superposé aux cellules, invisible : reçoit le focus, le collage et le
  // clavier système sans jamais s'afficher.
  hiddenInput: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: CELL_HEIGHT,
    opacity: 0,
    fontSize: typeScale.titleMd.fontSize,
  },
  error: { marginTop: space[3] },
});
