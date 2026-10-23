import { useState } from 'react';
import { StyleSheet, TextInput, View, type KeyboardTypeOptions } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';

import { useShake } from '@/components/money/useShake';
import { Spacer, Text } from '@/components/primitives';
import {
  layout,
  maxFontScale,
  radius,
  space,
  spring,
  stroke,
  type as typeScale,
  useTheme,
} from '@/theme';

export interface TextFieldProps {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  placeholder?: string;
  keyboardType?: KeyboardTypeOptions;
  autoCapitalize?: 'none' | 'sentences' | 'words';
  autoComplete?: 'email' | 'name' | 'tel' | 'off';
  error?: string | null;
  onSubmitEditing?: () => void;
  autoFocus?: boolean;
  testID?: string;
}

const FIELD_HEIGHT = 56;

/**
 * Champ de saisie texte.
 *
 * **Jamais utilisé pour un montant ni pour un PIN** : ceux-là passent par
 * `AmountKeypad` et `PinPad`, qui contrôlent la disposition, le timing et
 * l'haptique — trois choses que le clavier système ne laisse pas piloter.
 */
export function TextField({
  label,
  value,
  onChangeText,
  placeholder,
  keyboardType = 'default',
  autoCapitalize = 'none',
  autoComplete = 'off',
  error,
  onSubmitEditing,
  autoFocus = false,
  testID,
}: TextFieldProps) {
  const theme = useTheme();
  const shake = useShake();
  const [focused, setFocused] = useState(false);
  const scale = useSharedValue(1);

  // La validation se fait à la sortie du champ, jamais à chaque frappe :
  // marquer une erreur pendant la saisie est agressif et souvent faux.
  const [seenError, setSeenError] = useState(error);
  if (error !== seenError) {
    setSeenError(error);
    if (error) shake.trigger();
  }

  const animatedStyle = useAnimatedStyle(() => ({ transform: [{ scale: scale.value }] }));

  const borderColor = error
    ? theme.status.failed.fg
    : focused
      ? theme.accent.primary
      : theme.overlay.border;

  return (
    <View>
      <Text variant="labelMd" color="secondary">
        {label}
      </Text>
      <Spacer size={2} />

      <Animated.View style={[shake.style, animatedStyle]}>
        <TextInput
          value={value}
          onChangeText={onChangeText}
          onFocus={() => {
            setFocused(true);
            scale.value = withSpring(1.01, spring.snappy);
          }}
          onBlur={() => {
            setFocused(false);
            scale.value = withSpring(1, spring.snappy);
          }}
          placeholder={placeholder ?? ''}
          placeholderTextColor={theme.text.disabled}
          keyboardType={keyboardType}
          autoCapitalize={autoCapitalize}
          autoComplete={autoComplete}
          autoCorrect={false}
          autoFocus={autoFocus}
          {...(onSubmitEditing && { onSubmitEditing, returnKeyType: 'next' as const })}
          accessibilityLabel={label}
          // Sans plafond, un champ de 56 dp rogne son texte à 200 %.
          maxFontSizeMultiplier={maxFontScale.bodyLg}
          {...(testID !== undefined && { testID })}
          style={[
            styles.input,
            typeScale.bodyLg,
            {
              backgroundColor: theme.bg.surface2,
              color: theme.text.primary,
              borderColor,
              borderWidth: focused || error ? stroke.thick : stroke.hairline,
              borderRadius: radius.md,
            },
          ]}
        />
      </Animated.View>

      {error && (
        <>
          <Spacer size={2} />
          <Text variant="bodySm" tint={theme.status.failed.fg}>
            {error}
          </Text>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  input: {
    height: FIELD_HEIGHT,
    paddingHorizontal: space[4],
    minHeight: layout.minTouchTarget,
  },
});
