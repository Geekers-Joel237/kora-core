import { View } from 'react-native';

import { space as spaceScale, type SpaceToken } from '@/theme';

export interface SpacerProps {
  size: SpaceToken;
  axis?: 'vertical' | 'horizontal';
}

/**
 * L'espace négatif se choisit, il ne subsiste pas — §1.7.
 * Un `Spacer` rend cette décision explicite là où une marge la rendrait
 * implicite et disputée entre deux composants voisins.
 */
export function Spacer({ size, axis = 'vertical' }: SpacerProps) {
  const value = spaceScale[size];
  return <View style={axis === 'vertical' ? { height: value } : { width: value }} />;
}

/** Pousse les éléments suivants vers la fin de l'axe principal. */
export function Flex() {
  return <View style={{ flex: 1 }} />;
}
