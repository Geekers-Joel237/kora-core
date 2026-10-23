import { Text as RNText, type StyleProp, type TextStyle } from 'react-native';

import { maxFontScale, tabularNums, type, useTheme, type TypeToken } from '@/theme';

/** Rôles de couleur du texte — `docs/02-design-system.md` §3.3. */
export type TextColorRole =
  | 'primary'
  | 'secondary'
  | 'tertiary'
  | 'disabled'
  | 'onAccent'
  | 'danger'
  | 'success'
  | 'accent';

/**
 * Le style transmis ne peut porter ni couleur, ni fonte, ni métrique : ces
 * décisions appartiennent au design system et arrivent par `variant` et
 * `color`. Seuls la mise en page et l'alignement restent libres.
 */
type AllowedTextStyle = Omit<
  TextStyle,
  'color' | 'fontSize' | 'fontFamily' | 'fontWeight' | 'letterSpacing' | 'lineHeight'
>;

export interface TextProps {
  variant?: TypeToken;
  color?: TextColorRole;
  /**
   * Couleur résolue à l'exécution — puce d'état, sens de flux, marque
   * d'opérateur. **Doit provenir de `useTheme()`**, jamais d'un littéral : le
   * lint rejette toute couleur écrite en dur hors de `src/theme/`.
   *
   * Prend le pas sur `color`. Réservé aux cas où le rôle n'est connu qu'au
   * rendu ; partout ailleurs, utiliser `color`.
   */
  tint?: string;
  align?: TextStyle['textAlign'];
  numberOfLines?: number;
  /** Chiffres à chasse fixe — obligatoire sur tout montant. §3.4 */
  tabular?: boolean;
  style?: StyleProp<AllowedTextStyle>;
  accessibilityLabel?: string;
  children: React.ReactNode;
}

/**
 * Seule voie d'accès au texte dans l'application.
 *
 * `maxFontSizeMultiplier` applique le plafond de mise à l'échelle propre à la
 * variante (§3.5) : un `display` déborderait au-delà de 1,3×, un `body` n'a
 * aucun plafond fonctionnel.
 */
export function Text({
  variant = 'bodyMd',
  color = 'primary',
  tint,
  align,
  numberOfLines,
  tabular = false,
  style,
  accessibilityLabel,
  children,
}: TextProps) {
  const theme = useTheme();

  const resolvedColor =
    tint ?? (color === 'accent' ? theme.accent.primary : theme.text[color]);

  return (
    <RNText
      style={[
        type[variant],
        { color: resolvedColor },
        tabular && tabularNums,
        align !== undefined && { textAlign: align },
        style,
      ]}
      maxFontSizeMultiplier={maxFontScale[variant]}
      {...(numberOfLines !== undefined && { numberOfLines })}
      {...(accessibilityLabel !== undefined && { accessibilityLabel })}
    >
      {children}
    </RNText>
  );
}
