import { StyleSheet, type StyleProp, type ViewStyle } from 'react-native';

import { Icon, Pressable, type IconName } from '@/components/primitives';
import { layout, radius, useTheme, type IconSizeToken } from '@/theme';

export interface IconButtonProps {
  name: IconName;
  onPress: () => void;
  /** Obligatoire : une icône seule n'est jamais explicite pour un lecteur d'écran. */
  accessibilityLabel: string;
  size?: IconSizeToken;
  /** Pastille de fond. Sans elle, l'icône flotte sur le contenu. */
  surface?: boolean;
  tint?: string;
  disabled?: boolean;
  haptic?: 'tap' | 'select' | 'press' | 'none';
  style?: StyleProp<ViewStyle>;
  testID?: string;
}

/**
 * Bouton compact. Se comprime **plus** qu'un bouton pleine largeur (0,88 contre
 * 0,97) : à surface égale de perception, un petit élément doit bouger davantage
 * pour produire la même sensation. §4
 */
export function IconButton({
  name,
  onPress,
  accessibilityLabel,
  size = 'md',
  surface = false,
  tint,
  disabled = false,
  haptic = 'tap',
  style,
  testID,
}: IconButtonProps) {
  const theme = useTheme();

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      scale="icon"
      haptic={haptic}
      accessibilityLabel={accessibilityLabel}
      {...(testID !== undefined && { testID })}
      style={[
        styles.base,
        surface && { backgroundColor: theme.bg.surface2, borderRadius: radius.full },
        style,
      ]}
    >
      <Icon name={name} size={size} color={tint ?? theme.text.secondary} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    width: layout.minTouchTarget,
    height: layout.minTouchTarget,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
