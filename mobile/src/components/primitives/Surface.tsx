import { View, type StyleProp, type ViewStyle } from 'react-native';

import {
  radius as radiusScale,
  space as spaceScale,
  stroke,
  surfaceForElevation,
  useTheme,
  type ElevationLevel,
  type RadiusToken,
  type SpaceToken,
} from '@/theme';

export interface SurfaceProps {
  /**
   * En thème sombre, l'élévation est une **couleur de surface**, pas une ombre.
   * Seul le niveau 4 porte une ombre, pour détacher un élément flottant du
   * contenu qui défile dessous. §1.2, §5.3
   */
  elevation?: ElevationLevel;
  radius?: RadiusToken;
  padding?: SpaceToken;
  /**
   * Une bordure est un **dernier recours** (§1.5). Si elle semble nécessaire,
   * le contraste de surface est probablement insuffisant : c'est lui qu'il faut
   * corriger. Quand elle est inévitable, elle fait un pixel physique et sa
   * couleur est une superposition, jamais une couleur opaque.
   */
  bordered?: boolean;
  style?: StyleProp<ViewStyle>;
  children?: React.ReactNode;
}

export function Surface({
  elevation = 1,
  radius = 'lg',
  padding = 4,
  bordered = false,
  style,
  children,
}: SurfaceProps) {
  const theme = useTheme();

  return (
    <View
      style={[
        {
          backgroundColor: surfaceForElevation(theme, elevation),
          borderRadius: radiusScale[radius],
          padding: spaceScale[padding],
        },
        bordered && {
          borderWidth: stroke.hairline,
          borderColor: theme.overlay.border,
        },
        elevation === 4 && theme.floatingShadow,
        style,
      ]}
    >
      {children}
    </View>
  );
}
