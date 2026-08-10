import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { Slot } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SplashScreen from 'expo-splash-screen';
import { useFonts } from 'expo-font';
import { QueryClientProvider } from '@tanstack/react-query';

import { PrivacyShield, ToastProvider } from '@/components/feedback';
import { DevtoolsHost, initDevtools } from '@/devtools';
import { startNetworkWatch } from '@/lib/network';
import { createQueryClient } from '@/lib/queryClient';
import { persistQueryCache, restoreQueryCache } from '@/lib/queryPersistence';
import { useForegroundRevalidation } from '@/lib/useForegroundRevalidation';
import { fontFamily, ThemeProvider, useTheme, useThemeContext } from '@/theme';

// Créé une seule fois, hors du composant : le recréer à chaque rendu viderait
// le cache et transformerait chaque re-rendu en rafale de requêtes.
const queryClient = createQueryClient();

// Réhydratation **avant le premier rendu**, donc avant tout appel réseau —
// `docs/08-quality-bar.md` §4. MMKV est synchrone : c'est ce qui rend possible
// un démarrage à froid hors ligne avec des données à l'écran.
restoreQueryCache(queryClient);

// Avant tout : la surcharge d'URL, le journal réseau et le simulateur doivent
// être en place avant la première requête — y compris celle du portail de
// session. Sans objet en production, où ce module est un substitut inerte.
initDevtools();

// 05-screens.md §1 — l'écran de lancement natif reste affiché jusqu'à la
// résolution des polices. Aucun clignotement d'un écran intermédiaire.
void SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  // 02-design-system.md §3.1 — Inter est empaquetée localement, et seules les
  // quatre graisses du design system sont embarquées. Importer depuis la racine
  // de @expo-google-fonts/inter tirerait les 18 graisses (~6 Mo inutiles).
  const [fontsLoaded, fontError] = useFonts({
    [fontFamily.regular]: require('../assets/fonts/Inter-400Regular.ttf'),
    [fontFamily.medium]: require('../assets/fonts/Inter-500Medium.ttf'),
    [fontFamily.semibold]: require('../assets/fonts/Inter-600SemiBold.ttf'),
    [fontFamily.bold]: require('../assets/fonts/Inter-700Bold.ttf'),
  });

  useEffect(() => {
    // On masque aussi en cas d'échec de chargement : rester bloqué sur le
    // splash serait pire qu'un repli sur la police système.
    if (fontsLoaded || fontError) {
      void SplashScreen.hideAsync();
    }
  }, [fontsLoaded, fontError]);

  if (!fontsLoaded && !fontError) {
    return null;
  }

  return (
    <GestureHandlerRootView style={styles.root}>
      <SafeAreaProvider>
        <QueryClientProvider client={queryClient}>
          <ThemeProvider>
            <ToastProvider>
              <AppShell />
            </ToastProvider>
          </ThemeProvider>
        </QueryClientProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

/** Séparé du layout racine pour pouvoir consommer le thème qu'il fournit. */
function AppShell() {
  const theme = useTheme();
  const { scheme } = useThemeContext();

  useForegroundRevalidation();

  useEffect(() => startNetworkWatch(), []);
  useEffect(() => persistQueryCache(queryClient), []);

  return (
    <View style={[styles.root, { backgroundColor: theme.bg.app }]}>
      <StatusBar style={scheme === 'dark' ? 'light' : 'dark'} />
      <Slot />
      {/* Le panneau de validation flotte au-dessus de tout, y compris des
          écrans publics : le parcours d'authentification est le scénario 1. */}
      <DevtoolsHost />
      {/* Dernier élément de l'arbre : rien ne doit se dessiner par-dessus dans
          la vignette du sélecteur d'applications — NFR-44. */}
      <PrivacyShield />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
});
