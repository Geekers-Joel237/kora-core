import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Spacer, Text } from '@/components/primitives';
import { space, useTheme } from '@/theme';

/** Écran provisoire — remplacé au lot 9. */
export default function SettingsScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <View
      style={[styles.container, { backgroundColor: theme.bg.app, paddingTop: insets.top + space[8] }]}
    >
      <Text variant="titleLg">Réglages</Text>
      <Spacer size={2} />
      <Text variant="bodyMd" color="secondary">
        Cet écran arrive au lot 9.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, paddingHorizontal: space[5] },
});
