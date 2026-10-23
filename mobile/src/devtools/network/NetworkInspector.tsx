import { useCallback, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import * as Clipboard from 'expo-clipboard';

import { Button } from '@/components/action';
import { useToast } from '@/components/feedback';
import { Divider, Icon, Pressable, Spacer, Surface, Text } from '@/components/primitives';
import { currentApiUrl } from '@/devtools/environment/store';
import { getApiBaseUrl, request } from '@/lib/http';
import { radius, space, useTheme } from '@/theme';
import { isReplayable, toCurl } from './curl';
import { MAX_ENTRIES, signalsOf, useNetworkLog, type NetworkEntry, type NetworkSignal } from './store';

/** Correspondance des signaux du §3 avec les rôles de couleur du design system. */
const SIGNAL_ICONS: Record<NetworkSignal, 'x-circle' | 'clock' | 'refresh-cw' | 'rotate-ccw'> = {
  error: 'x-circle',
  slow: 'clock',
  refresh: 'refresh-cw',
  replay: 'rotate-ccw',
};

const SIGNAL_LABELS: Record<NetworkSignal, string> = {
  error: 'Erreur',
  slow: 'Lente',
  refresh: 'Rafraîchissement de jeton',
  replay: 'Rejeu après rafraîchissement',
};

/**
 * Inspecteur réseau — `docs/10-validation-mode.md` §3.
 *
 * « Le composant le plus utile du mode. » Le signal jaune est le plus précieux :
 * il rend visible la mécanique de rafraîchissement du §3.4 de
 * `06-architecture.md`, autrement totalement invisible — donc impossible à
 * déboguer autrement qu'en devinant.
 *
 * ⚠️ Aucun secret ne transite ici : le masquage a lieu dans `lib/http`, avant
 * que l'entrée n'existe. Cet écran n'a **jamais** vu un `rawPin`.
 */
export function NetworkInspector() {
  const theme = useTheme();
  const toast = useToast();
  const entries = useNetworkLog((state) => state.entries);
  const clear = useNetworkLog((state) => state.clear);
  const [expanded, setExpanded] = useState<string | null>(null);

  const signalColor = useCallback(
    (signal: NetworkSignal) => {
      if (signal === 'error') return theme.status.failed.fg;
      if (signal === 'slow') return theme.status.pending.fg;
      if (signal === 'refresh') return theme.status.reversed.fg;
      return theme.accent.primary;
    },
    [theme],
  );

  const copyCurl = useCallback(
    async (entry: NetworkEntry) => {
      await Clipboard.setStringAsync(toCurl(entry, currentApiUrl()));
      toast.show({ message: 'cURL copié — secrets masqués', icon: 'copy', tone: 'success' });
    },
    [toast],
  );

  const replay = useCallback(
    async (entry: NetworkEntry) => {
      // §3 — rejeu réservé aux `GET`. Jamais sur un POST de paiement.
      await request(entry.path, { query: entry.query }).catch(() => undefined);
      toast.show({ message: 'Requête rejouée', icon: 'refresh-cw' });
    },
    [toast],
  );

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      <View style={styles.toolbar}>
        <View style={styles.toolbarText}>
          <Text variant="labelMd">
            {entries.length} / {MAX_ENTRIES} entrées
          </Text>
          <Text variant="bodySm" color="tertiary" numberOfLines={1}>
            {getApiBaseUrl()}
          </Text>
        </View>
        <Button
          label="Vider"
          variant="ghost"
          size="sm"
          fullWidth={false}
          disabled={entries.length === 0}
          onPress={clear}
          testID="network-clear"
        />
      </View>

      <Spacer size={4} />

      {entries.length === 0 && (
        <Text variant="bodySm" color="tertiary">
          Aucune requête depuis l’ouverture de l’application.
        </Text>
      )}

      {entries.map((entry) => {
        const signals = signalsOf(entry);
        const status = entry.response?.status ?? null;

        return (
          <View key={entry.id} style={styles.card}>
            <Surface elevation={1} padding={4}>
              <Pressable
                onPress={() => setExpanded((current) => (current === entry.id ? null : entry.id))}
                haptic="tap"
                scale="card"
                accessibilityLabel={`${entry.method} ${entry.path}, statut ${status ?? 'en cours'}`}
                testID={`network-${entry.id}`}
              >
                <View style={styles.row}>
                  <Text variant="monoMd" numberOfLines={1} style={styles.grow}>
                    {entry.method} {entry.path}
                  </Text>
                  <View
                    style={[
                      styles.statusTag,
                      {
                        backgroundColor:
                          status === null
                            ? theme.bg.surface3
                            : status >= 400 || status === 0
                              ? theme.status.failed.bg
                              : theme.status.success.bg,
                        borderRadius: radius.xs,
                      },
                    ]}
                  >
                    <Text variant="labelSm" color="secondary">
                      {status ?? '…'}
                    </Text>
                  </View>
                </View>

                <Spacer size={1} />
                <Text variant="bodySm" color="tertiary">
                  {new Date(entry.startedAt).toLocaleTimeString('fr-FR')}
                  {entry.response ? ` · ${entry.response.durationMs} ms` : ' · en cours'}
                </Text>

                {signals.length > 0 && (
                  <>
                    <Spacer size={2} />
                    <View style={styles.signals}>
                      {signals.map((signal) => (
                        <View key={signal} style={styles.signal}>
                          <Icon name={SIGNAL_ICONS[signal]} size="xs" color={signalColor(signal)} />
                          <Text variant="labelSm" tint={signalColor(signal)}>
                            {SIGNAL_LABELS[signal]}
                          </Text>
                        </View>
                      ))}
                    </View>
                  </>
                )}
              </Pressable>

              {expanded === entry.id && (
                <>
                  <Spacer size={3} />
                  <Divider />
                  <Spacer size={3} />

                  <Field label="Corrélation" value={entry.correlationId} />
                  <Field label="En-têtes" value={JSON.stringify(entry.headers, null, 2)} />
                  {entry.body !== undefined && (
                    <Field label="Corps envoyé" value={JSON.stringify(entry.body, null, 2)} />
                  )}
                  {entry.response && (
                    <Field
                      label={entry.response.transportError ? 'Erreur de transport' : 'Réponse'}
                      value={
                        entry.response.transportError ??
                        JSON.stringify(entry.response.body, null, 2)
                      }
                    />
                  )}

                  <Spacer size={3} />
                  <Button
                    label="Copier en cURL"
                    variant="secondary"
                    size="sm"
                    icon="copy"
                    onPress={() => void copyCurl(entry)}
                    testID={`network-curl-${entry.id}`}
                  />
                  {isReplayable(entry) && (
                    <>
                      <Spacer size={2} />
                      <Button
                        label="Rejouer"
                        variant="ghost"
                        size="sm"
                        onPress={() => void replay(entry)}
                        testID={`network-replay-${entry.id}`}
                      />
                    </>
                  )}
                  {!isReplayable(entry) && (
                    <>
                      <Spacer size={2} />
                      <Text variant="bodySm" color="tertiary">
                        Rejeu indisponible sur un `POST` — sans idempotence serveur, il pourrait
                        débiter deux fois.
                      </Text>
                    </>
                  )}
                </>
              )}
            </Surface>
          </View>
        );
      })}
    </ScrollView>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.field}>
      <Text variant="labelSm" color="tertiary">
        {label.toUpperCase()}
      </Text>
      <Text variant="monoMd" color="secondary">
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingBottom: space[8] },
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space[3],
  },
  toolbarText: { flexShrink: 1, gap: space[1] },
  card: { marginBottom: space[3] },
  row: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
  grow: { flexShrink: 1, flexGrow: 1 },
  statusTag: { paddingHorizontal: space[2], paddingVertical: space[1] },
  signals: { flexDirection: 'row', flexWrap: 'wrap', gap: space[3] },
  signal: { flexDirection: 'row', alignItems: 'center', gap: space[1] },
  field: { gap: space[1], paddingBottom: space[3] },
});
