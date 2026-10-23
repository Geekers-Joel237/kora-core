import { StyleSheet, View } from 'react-native';

import { Icon, Pressable, Text } from '@/components/primitives';
import { layout, space, useTheme } from '@/theme';

export interface OptionRowProps {
  label: string;
  description?: string;
  selected: boolean;
  onPress: () => void;
  testID?: string;
}

/**
 * Ligne d'un choix unique.
 *
 * La coche est **à droite** et n'apparaît que sur l'élément retenu : une liste
 * de onze états où chaque ligne porte un cercle vide se lit comme un formulaire.
 * Ici, un seul signal, à un seul endroit.
 */
export function OptionRow({ label, description, selected, onPress, testID }: OptionRowProps) {
  const theme = useTheme();

  return (
    <Pressable
      onPress={onPress}
      haptic="select"
      scale="card"
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected }}
      accessibilityLabel={label}
      testID={testID}
      style={styles.row}
    >
      <View style={styles.body}>
        <Text variant="bodyMd" color={selected ? 'primary' : 'secondary'}>
          {label}
        </Text>
        {description !== undefined && description !== '' && (
          <Text variant="bodySm" color="tertiary" numberOfLines={1}>
            {description}
          </Text>
        )}
      </View>
      {selected && <Icon name="check-circle" size="sm" color={theme.accent.primary} />}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    minHeight: layout.minTouchTarget,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space[3],
    paddingVertical: space[2],
  },
  body: { flex: 1, gap: space[1] },
});