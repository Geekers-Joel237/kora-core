import { useEffect } from 'react';
import { StyleSheet, Text as RNText, TextInput, View } from 'react-native';
import Animated, {
  useAnimatedProps,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

import { Text } from '@/components/primitives';
import { formatMinor, THIN_NBSP, type SignPolicy } from '@/lib/money';
import { easeOutCubic } from '@/theme/easing';
import {
  currencySymbolStyle,
  maxFontScale,
  space,
  tabularNums,
  type as typeScale,
  useReduceMotion,
  useTheme,
  type TypeToken,
} from '@/theme';
import type { Direction } from '@/types/domain';

/** Tailles admises pour un montant. Un montant ne se rend jamais en `label*`. */
export type AmountSize = Extract<
  TypeToken,
  'displayXl' | 'displayLg' | 'displayMd' | 'titleLg' | 'titleMd' | 'bodyLg' | 'bodyMd'
>;

export interface AmountProps {
  /** Entier dans la plus petite unité. **Jamais un flottant.** Contrat §5.3. */
  minor: number;
  currency: string;
  size?: AmountSize;
  sign?: SignPolicy;
  /** Pilote la couleur — design system §2.5. */
  direction?: Direction;
  /** Couleur explicite. Prend le pas sur `direction`. */
  tint?: string;
  hidden?: boolean;
  /** Compteur animé — §6.1. Réservé au solde et aux écrans de résultat. */
  animate?: boolean;
  align?: 'left' | 'center' | 'right';
  testID?: string;
}

/** Durée du compteur — §6.1. Rapide puis décélération marquée. */
const COUNT_DURATION_MS = 900;

/**
 * Rendu canonique de tout montant de l'application.
 *
 * **Aucun montant ne s'affiche autrement.** Applique intégralement le §3.4 du
 * design system : trois blocs typographiques distincts, espace fine insécable
 * `U+202F`, signe moins mathématique `U+2212`, chiffres à chasse fixe, symbole
 * à 0,45× la taille du montant.
 */
export function Amount({
  minor,
  currency,
  size = 'bodyLg',
  sign = 'auto',
  direction,
  tint,
  hidden = false,
  animate = false,
  align = 'left',
  testID,
}: AmountProps) {
  const theme = useTheme();

  const color =
    tint ??
    (direction === 'INBOUND'
      ? theme.flow.inbound
      : direction === 'OUTBOUND'
        ? theme.flow.outbound
        : theme.text.primary);

  const parts = formatMinor(minor, currency, { sign, hidden });

  return (
    <View
      style={[styles.row, align === 'center' && styles.center, align === 'right' && styles.right]}
      accessibilityRole="text"
      // NFR-54 — le lecteur d'écran annonce le montant en toutes lettres,
      // pas caractère par caractère.
      accessibilityLabel={`${parts.sign}${parts.integer.replace(
        new RegExp(THIN_NBSP, 'g'),
        '',
      )} ${currency}`}
      {...(testID !== undefined && { testID })}
    >
      {parts.sign !== '' && (
        <Text variant={size} tint={color} tabular>
          {parts.sign}
        </Text>
      )}

      {animate && !hidden ? (
        <AnimatedInteger minor={Math.abs(minor)} color={color} size={size} />
      ) : (
        <Text variant={size} tint={color} tabular>
          {parts.integer}
        </Text>
      )}

      {parts.fraction !== null && (
        <Text variant={size} tint={color} tabular>
          ,{parts.fraction}
        </Text>
      )}

      <View style={styles.symbol}>
        <RNText
          style={[currencySymbolStyle(size), { color: theme.text.secondary }]}
          maxFontSizeMultiplier={maxFontScale[size]}
        >
          {parts.symbol}
        </RNText>
      </View>
    </View>
  );
}

const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

// `text` n'est pas une propriété publique de `TextInputProps` : c'est une
// propriété native que Reanimated doit être autorisé à piloter directement.
// Sans cet enregistrement, la valeur ne franchit pas la frontière native.
Animated.addWhitelistedNativeProps({ text: true });

/**
 * Compteur animé — §6.1.
 *
 * Le texte est piloté par `animatedProps` sur un `TextInput` non éditable :
 * c'est la seule façon de faire varier un contenu textuel **sur le thread UI**.
 * Passer par un `useState` remettrait le compteur sur le pont JS, et 60 rendus
 * React par seconde décrocheraient sur l'appareil socle.
 *
 * Le groupement des milliers est recalculé à chaque image, sans expression
 * régulière : les worklets tolèrent mal `String.replace` avec un `RegExp`.
 */
function AnimatedInteger({
  minor,
  color,
  size,
}: {
  minor: number;
  color: string;
  size: AmountSize;
}) {
  const reduceMotion = useReduceMotion();
  const value = useSharedValue(minor);

  useEffect(() => {
    if (reduceMotion) {
      // §7.2 — le compteur affiche directement la valeur finale.
      value.value = minor;
      return;
    }
    value.value = withTiming(minor, {
      duration: COUNT_DURATION_MS,
      easing: easeOutCubic,
    });
  }, [minor, reduceMotion, value]);

  const animatedProps = useAnimatedProps(() => {
    'worklet';
    // Le cast est nécessaire : `text` sort du type public de TextInput.
    return { text: groupThousandsWorklet(Math.round(value.value)) } as never;
  });

  return (
    <AnimatedTextInput
      editable={false}
      // `value` initial : évite un cadre vide avant la première image animée.
      defaultValue={groupThousandsWorklet(minor)}
      animatedProps={animatedProps}
      style={[
        typeScale[size],
        tabularNums,
        styles.counter,
        { color },
      ]}
      maxFontSizeMultiplier={maxFontScale[size]}
      accessible={false}
      importantForAccessibility="no"
      pointerEvents="none"
      underlineColorAndroid="transparent"
    />
  );
}

/**
 * Groupement des milliers, exécutable dans un worklet.
 * Boucle explicite plutôt que `replace(/\B(?=(\d{3})+(?!\d))/g, …)` : la
 * seconde forme n'est pas fiable hors du runtime JavaScript principal.
 */
function groupThousandsWorklet(value: number): string {
  'worklet';
  const digits = String(Math.max(0, value));
  let out = '';
  for (let index = 0; index < digits.length; index += 1) {
    if (index > 0 && (digits.length - index) % 3 === 0) out += THIN_NBSP;
    out += digits[index];
  }
  return out;
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'baseline' },
  center: { justifyContent: 'center' },
  right: { justifyContent: 'flex-end' },
  symbol: { marginLeft: space[1] },
  // Un TextInput porte des marges internes par défaut qui décaleraient le
  // montant par rapport aux blocs voisins rendus en Text.
  counter: { padding: 0, margin: 0, textAlign: 'left' },
});
