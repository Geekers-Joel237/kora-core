import { Tabs } from 'expo-router';

import { Icon } from '@/components/primitives';
import { layout, type as typeScale, useTheme } from '@/theme';

export default function TabsLayout() {
  const theme = useTheme();

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: theme.accent.primary,
        tabBarInactiveTintColor: theme.text.tertiary,
        tabBarStyle: {
          backgroundColor: theme.bg.app,
          borderTopColor: theme.overlay.hairline,
          height: layout.tabBarHeight,
        },
        tabBarLabelStyle: typeScale.labelSm,
      }}
    >
      <Tabs.Screen
        name="home"
        options={{
          title: 'Accueil',
          tabBarIcon: ({ color }) => <Icon name="home" size="md" color={color} />,
        }}
      />
      <Tabs.Screen
        name="activity"
        options={{
          title: 'Activité',
          tabBarIcon: ({ color }) => <Icon name="activity" size="md" color={color} />,
        }}
      />
      <Tabs.Screen
        name="settings"
        options={{
          title: 'Réglages',
          tabBarIcon: ({ color }) => <Icon name="settings" size="md" color={color} />,
        }}
      />
    </Tabs>
  );
}
