import { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/action';
import { Segmented, TextField } from '@/components/input';
import { Divider, Spacer, Surface, Text } from '@/components/primitives';
import { space, useTheme } from '@/theme';
import {
  armForcedResponse,
  armNetworkCut,
  MAX_LATENCY_MS,
  resetSimulation,
  setLatency,
  useSimulation,
} from './store';

const LATENCY_STEPS = ['0', '500', '2000', '5000'] as const;
type LatencyStep = (typeof LATENCY_STEPS)[number];

const FORCED_STATUSES = ['401', '422', '503'] as const;
type ForcedStatus = (typeof FORCED_STATUSES)[number];

const STATUS_DETAILS: Record<ForcedStatus, string> = {
  '401': 'Unauthorized',
  '422': 'Insufficient funds',
  '503': 'Service unavailable',
};

const DEFAULT_CUT_MS = 800;

/**
 * Simulation d'échec — `docs/10-validation-mode.md` §7.
 *
 * Le décor par défaut vise le scénario que le §7 désigne comme **le plus
 * important de toute l'application** : couper le réseau à 800 ms au milieu d'un
 * `POST /payments/*`. C'est la seule façon d'observer ce que le backend fait
 * quand un client disparaît en cours de transaction, et de vérifier que l'écran
 * « issue incertaine » se comporte correctement.
 */
export function SimulationScreen() {
  const theme = useTheme();
  const latencyMs = useSimulation((state) => state.latencyMs);
  const forced = useSimulation((state) => state.forced);
  const cut = useSimulation((state) => state.cut);
  const fired = useSimulation((state) => state.fired);

  const [pathFragment, setPathFragment] = useState('/payments/');
  const [cutMs, setCutMs] = useState(String(DEFAULT_CUT_MS));

  const latencyValue = String(latencyMs) as LatencyStep;

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
      keyboardShouldPersistTaps="handled"
    >
      <Surface elevation={1} padding={4}>
        <Text variant="labelMd">Latence forcée</Text>
        <Spacer size={1} />
        <Text variant="bodySm" color="tertiary">
          Ajoutée à **toute** réponse, jusqu’à {MAX_LATENCY_MS / 1000} s. Persistante.
        </Text>
        <Spacer size={3} />
        <Segmented
          options={LATENCY_STEPS.map((step) => ({
            value: step,
            label: step === '0' ? 'Aucune' : `${Number(step) / 1000} s`,
          }))}
          value={LATENCY_STEPS.includes(latencyValue) ? latencyValue : '0'}
          onChange={(next) => setLatency(Number(next ?? '0'))}
          accessibilityLabel="Latence forcée"
          testID="sim-latency"
        />
      </Surface>

      <Spacer size={5} />

      <TextField
        label="Chemin ciblé"
        value={pathFragment}
        onChangeText={setPathFragment}
        placeholder="/payments/"
        testID="sim-path"
      />

      <Spacer size={5} />

      <Surface elevation={1} padding={4}>
        <Text variant="labelMd">Réponse imposée</Text>
        <Spacer size={1} />
        <Text variant="bodySm" color="tertiary">
          Armée pour le **prochain** appel correspondant, puis désarmée.
        </Text>
        <Spacer size={3} />
        <View style={styles.actions}>
          {FORCED_STATUSES.map((status) => (
            <Button
              key={status}
              label={status}
              variant="secondary"
              size="sm"
              fullWidth={false}
              onPress={() =>
                armForcedResponse({
                  pathFragment,
                  status: Number(status),
                  detail: STATUS_DETAILS[status],
                })
              }
              testID={`sim-force-${status}`}
            />
          ))}
        </View>
        {forced && (
          <>
            <Spacer size={2} />
            <Text variant="bodySm" tint={theme.status.pending.fg}>
              Armé : {forced.status} sur `{forced.pathFragment}`
            </Text>
          </>
        )}
      </Surface>

      <Spacer size={5} />

      <Surface elevation={1} padding={4}>
        <Text variant="labelMd">Coupure réseau</Text>
        <Spacer size={1} />
        <Text variant="bodySm" color="tertiary">
          Le scénario le plus important à valider : couper au milieu d’un
          `POST /payments/*` doit produire l’écran « issue incertaine », sans
          aucun rejeu automatique.
        </Text>
        <Spacer size={3} />
        <TextField
          label="Couper après (ms)"
          value={cutMs}
          onChangeText={(value) => setCutMs(value.replace(/\D/g, ''))}
          keyboardType="number-pad"
          testID="sim-cut-ms"
        />
        <Spacer size={3} />
        <Button
          label="Armer la coupure"
          onPress={() =>
            armNetworkCut({ pathFragment, afterMs: Number(cutMs) || DEFAULT_CUT_MS })
          }
          testID="sim-arm-cut"
        />
        {cut && (
          <>
            <Spacer size={2} />
            <Text variant="bodySm" tint={theme.status.pending.fg}>
              Armé : coupure à {cut.afterMs} ms sur `{cut.pathFragment}`
            </Text>
          </>
        )}
      </Surface>

      <Spacer size={5} />
      <Divider />
      <Spacer size={4} />

      <View style={styles.actions}>
        <Text variant="bodySm" color="tertiary">
          {fired} injection{fired > 1 ? 's' : ''} consommée{fired > 1 ? 's' : ''}
        </Text>
      </View>
      <Spacer size={3} />
      <Button
        label="Tout désarmer"
        variant="ghost"
        onPress={resetSimulation}
        testID="sim-reset"
      />

      <Spacer size={4} />
      <Text variant="bodySm" color="tertiary">
        L’expiration de jeton se déclenche depuis l’onglet Session — elle porte
        sur l’état, pas sur une requête.
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingBottom: space[8] },
  actions: { flexDirection: 'row', alignItems: 'center', gap: space[2], flexWrap: 'wrap' },
});
