import { StyleSheet, View, type ViewStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { space, stroke, useTheme } from '@/theme';

export interface ActionBarProps {
  children: React.ReactNode;
  style?: ViewStyle;
}

/**
 * Barre ancrée portant l'action principale — `docs/04-components.md` §6.
 *
 * La vue défilante au-dessus doit réserver `layout.anchoredBarClearance` en
 * padding bas : sans cela, le dernier élément de contenu reste masqué sous la
 * barre, ce qui est invisible en développement et évident sur un vrai appareil.
 */
export function ActionBar({ children, style }: ActionBarProps) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <View
      style={[
        styles.bar,
        {
          backgroundColor: theme.bg.app,
          borderTopWidth: stroke.hairline,
          borderTopColor: theme.overlay.hairline,
          paddingBottom: insets.bottom + space[4],
        },
        style,
      ]}
    >
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { paddingHorizontal: space[5], paddingTop: space[4], gap: space[3] },
});
