import { StyleSheet, View } from 'react-native';

import { Text } from '@/components/primitives';
import { space, useTheme } from '@/theme';

export interface DayHeaderProps {
  label: string;
}

/**
 * En-tête de journée, épinglé en haut de liste pendant le défilement.
 *
 * Le fond est **opaque**, pas translucide : une teinte transparente laisserait
 * les lignes défiler visiblement dessous, ce qui donne l'impression que
 * l'en-tête est cassé plutôt que fixé.
 */
export function DayHeader({ label }: DayHeaderProps) {
  const theme = useTheme();

  return (
    <View style={[styles.header, { backgroundColor: theme.bg.app }]}>
      <Text variant="labelSm" color="tertiary">
        {label.toUpperCase()}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    paddingHorizontal: space[4],
    paddingTop: space[5],
    paddingBottom: space[2],
  },
});