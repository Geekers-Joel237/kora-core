import { View } from 'react-native';

import { space as spaceScale, stroke, useTheme, type SpaceToken } from '@/theme';

export interface DividerProps {
  orientation?: 'horizontal' | 'vertical';
  /** Retrait latéral, aligné sur l'échelle d'espacement. */
  inset?: SpaceToken;
  /** Superposition plus marquée, pour séparer deux sections plutôt que deux lignes. */
  strong?: boolean;
}

/**
 * Filet d'un **pixel physique**, jamais 1 dp : sur un écran à 3× de densité,
 * un trait de 1 dp fait trois pixels et se voit comme un trait épais.
 */
export function Divider({ orientation = 'horizontal', inset = 0, strong = false }: DividerProps) {
  const theme = useTheme();
  const color = strong ? theme.overlay.border : theme.overlay.hairline;

  if (orientation === 'vertical') {
    return <View style={{ width: stroke.hairline, alignSelf: 'stretch', backgroundColor: color, marginVertical: spaceScale[inset] }} />;
  }

  return (
    <View
      style={{
        height: stroke.hairline,
        alignSelf: 'stretch',
        backgroundColor: color,
        marginHorizontal: spaceScale[inset],
      }}
    />
  );
}
