import { useCallback, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/action';
import { TextField } from '@/components/input';
import { Divider, Spacer, Surface, Text } from '@/components/primitives';
import { env } from '@/lib/env';
import { space, useTheme } from '@/theme';
import {
  currentApiUrl,
  ENVIRONMENT_PRESETS,
  HEALTH_PATH,
  isOverridden,
  switchEnvironment,
  testConnectivity,
  type ConnectivityResult,
} from './store';

/**
 * Bascule d'environnement — `docs/10-validation-mode.md` §6.
 *
 * Le test de connectivité précède **toujours** la bascule : basculer vers une
 * URL injoignable purge la session pour rien, et laisse l'application dans un
 * état où plus rien ne fonctionne sans qu'on sache pourquoi.
 */
export function EnvironmentScreen() {
  const theme = useTheme();
  const [customUrl, setCustomUrl] = useState('');
  const [probing, setProbing] = useState<string | null>(null);
  const [results, setResults] = useState<Record<string, ConnectivityResult>>({});

  const probe = useCallback(async (url: string) => {
    setProbing(url);
    const result = await testConnectivity(url);
    setResults((current) => ({ ...current, [url]: result }));
    setProbing(null);
  }, []);

  const targets = [
    ...ENVIRONMENT_PRESETS,
    ...(customUrl.trim()
      ? [
          {
            id: 'custom',
            label: 'Appareil physique',
            hint: 'IP locale de la machine de développement',
            url: customUrl.trim(),
          },
        ]
      : []),
  ];

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
      keyboardShouldPersistTaps="handled"
    >
      <Surface elevation={1} padding={4}>
        <Text variant="labelMd">Environnement actif</Text>
        <Spacer size={2} />
        <Text variant="monoMd" numberOfLines={1}>
          {currentApiUrl()}
        </Text>
        <Spacer size={1} />
        <Text variant="bodySm" color="tertiary">
          {isOverridden() ? `Surcharge locale · build : ${env.apiUrl}` : 'Valeur de la build'}
        </Text>
      </Surface>

      <Spacer size={5} />

      <TextField
        label="Appareil physique — IP locale"
        value={customUrl}
        onChangeText={setCustomUrl}
        placeholder="http://192.168.1.10:8081"
        keyboardType="url"
        testID="env-custom-url"
      />

      <Spacer size={5} />

      {targets.map((preset) => {
        const result = results[preset.url];
        const active = preset.url === currentApiUrl();

        return (
          <View key={preset.id} style={styles.card}>
            <Surface elevation={1} padding={4} bordered={active}>
              <Text variant="labelMd">{preset.label}</Text>
              <Text variant="monoMd" color="secondary" numberOfLines={1}>
                {preset.url}
              </Text>
              <Text variant="bodySm" color="tertiary">
                {preset.hint}
              </Text>

              {result && (
                <>
                  <Spacer size={2} />
                  <Text
                    variant="bodySm"
                    tint={result.ok ? theme.status.success.fg : theme.status.failed.fg}
                  >
                    {result.ok
                      ? `${HEALTH_PATH} → ${result.status} en ${result.durationMs} ms`
                      : `${HEALTH_PATH} → ${result.reason}`}
                  </Text>
                </>
              )}

              <Spacer size={3} />
              <Divider />
              <Spacer size={3} />

              <View style={styles.actions}>
                <Button
                  label="Tester"
                  variant="secondary"
                  size="sm"
                  fullWidth={false}
                  loading={probing === preset.url}
                  onPress={() => void probe(preset.url)}
                  testID={`env-test-${preset.id}`}
                />
                <Button
                  label={active ? 'Actif' : 'Basculer'}
                  size="sm"
                  fullWidth={false}
                  disabled={active}
                  onPress={() => void switchEnvironment(preset.url)}
                  testID={`env-switch-${preset.id}`}
                />
              </View>
            </Surface>
          </View>
        );
      })}

      <Spacer size={4} />
      <Button
        label="Revenir à l’URL de la build"
        variant="ghost"
        disabled={!isOverridden()}
        onPress={() => void switchEnvironment(null)}
        testID="env-reset"
      />

      <Spacer size={4} />
      <Text variant="bodySm" color="tertiary">
        Une bascule purge SecureStore, le cache de requêtes et le stockage local,
        puis relance l’application. Mélanger des jetons entre environnements
        produit des symptômes incompréhensibles — §6.
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingBottom: space[8] },
  card: { marginBottom: space[3] },
  actions: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
});
