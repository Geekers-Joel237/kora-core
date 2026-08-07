import type { ColorValue } from 'react-native';
import Svg, { Circle, Line, Path, Rect } from 'react-native-svg';

import { iconSize, stroke, useTheme, type IconSizeToken } from '@/theme';
import { ICONS, type IconName, type IconShape } from './icons';

export interface IconProps {
  name: IconName;
  /** Tailles autorisées : 16, 20, 24, 28, 32. Aucune autre. §6.1 */
  size?: IconSizeToken;
  /**
   * Couleur explicite. Par défaut, le texte primaire du thème.
   * `ColorValue` et non `string` : react-navigation transmet des couleurs
   * opaques natives à `tabBarIcon`.
   */
  color?: ColorValue;
}

/**
 * Icônes en SVG vectoriel — **jamais** une police d'icônes, jamais un PNG :
 * l'un rend flou en haute densité, l'autre ne se teinte pas.
 *
 * Le trait fait 1,5 dp et n'est **jamais mis à l'échelle** : `vectorEffect`
 * n'existant pas partout, on garde une viewBox 24×24 fixe et on laisse le
 * conteneur redimensionner — le trait suit donc la taille, ce qui est le
 * comportement voulu pour un jeu conçu sur une seule grille.
 */
export function Icon({ name, size = 'md', color }: IconProps) {
  const theme = useTheme();
  const resolved = color ?? theme.text.primary;
  const dimension = iconSize[size];

  return (
    <Svg
      width={dimension}
      height={dimension}
      viewBox="0 0 24 24"
      fill="none"
      stroke={resolved}
      strokeWidth={stroke.regular}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {ICONS[name].map((shape, index) => renderShape(shape, index, resolved))}
    </Svg>
  );
}

function renderShape(shape: IconShape, key: number, color: ColorValue) {
  switch (shape.kind) {
    case 'path':
      return <Path key={key} d={shape.d} />;
    case 'circle':
      return (
        <Circle
          key={key}
          cx={shape.cx}
          cy={shape.cy}
          r={shape.r}
          {...(shape.filled && { fill: color })}
        />
      );
    case 'rect':
      return (
        <Rect
          key={key}
          x={shape.x}
          y={shape.y}
          width={shape.w}
          height={shape.h}
          rx={shape.rx}
        />
      );
    case 'line':
      return <Line key={key} x1={shape.x1} y1={shape.y1} x2={shape.x2} y2={shape.y2} />;
  }
}

export type { IconName };
