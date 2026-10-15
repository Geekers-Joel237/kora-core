import { useCallback, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';

import { Button } from '@/components/action';
import { useToast } from '@/components/feedback';
import { Pressable, Spacer, Surface, Text } from '@/components/primitives';
import { request } from '@/lib/http';
import { radius, space, useTheme } from '@/theme';

/** Sondage forcé, **sans plafond** — `docs/10-validation-mode.md` §4. */
const FORCED_POLL_MS = 2000;
const INSPECTOR_LIMIT = 50;

interface RawItem {
  reference?: unknown;
  kind?: unknown;
  status?: unknown;
  amount?: unknown;
  currency?: unknown;
  counterpart?: unknown;
  channel?: unknown;
  date?: unknown;
}

interface RawHistory {
  items: RawItem[];
  nextCursor: unknown;
}

/**
 * Requête brute — **exception assumée à la règle R1**.
 *
 * Les écrans produit ne voient que des types de domaine. L'inspecteur, lui,
 * existe précisément pour montrer ce que le serveur envoie : aucune traduction,
 * aucun schéma. Une charge utile qui ne ressemble pas au contrat s'affiche
 * telle quelle plutôt que de faire échouer l'écran. Ce module ne quitte jamais
 * `src/devtools/`.
 */
async function fetchRawHistory(): Promise<RawHistory> {
  const raw = await request<unknown>('/payments/history', { query: { limit: INSPECTOR_LIMIT } });
  const body = typeof raw === 'object' && raw !== null ? (raw as Record<string, unknown>) : {};
  return {
    items: Array.isArray(body.items) ? (body.items as RawItem[]) : [],
    nextCursor: body.nextCursor ?? null,
  };
}

function text(value: unknown): string {
  return value === null || value === undefined ? '∅' : String(value);
}

/**
 * Inspecteur de transactions — `docs/10-validation-mode.md` §4.
 *
 * Vue brute de la réponse client : statut et nature tels que le serveur les
 * nomme, montant signé, curseur suivant. L'historique d'états n'est plus exposé
 * au client (backend, 2026-10-13) : les durées inter-états ne se mesurent plus
 * depuis l'app.
 */
export function TransactionInspector() {
  const theme = useTheme();
  const toast = useToast();
  const [polling, setPolling] = useState(false);
  const [expanded, setExpanded] = useState<string | null>(null);

  const history = useQuery({
    queryKey: ['devtools', 'raw-history'] as const,
    queryFn: fetchRawHistory,
    // Sondage forcé sans plafond : c'est un outil d'observation, pas un écran
    // produit. Il s'arrête quand l'onglet est fermé.
    refetchInterval: polling ? FORCED_POLL_MS : false,
    staleTime: 0,
  });

  const copy = useCallback(
    async (payload: unknown, label: string) => {
      await Clipboard.setStringAsync(JSON.stringify(payload, null, 2));
      toast.show({ message: `${label} copié`, icon: 'copy', tone: 'success' });
    },
    [toast],
  );

  const items = history.data?.items ?? [];

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      <View style={styles.toolbar}>
        <View style={styles.toolbarText}>
          <Text variant="labelMd">
            {items.length} opération{items.length > 1 ? 's' : ''} · limit={INSPECTOR_LIMIT}
          </Text>
          <Text variant="bodySm" color="tertiary">
            {polling ? `suivi forcé toutes les ${FORCED_POLL_MS / 1000} s` : 'suivi à l’arrêt'}
            {history.isFetching ? ' · en cours' : ''}
          </Text>
          <Text variant="bodySm" color="tertiary" numberOfLines={1}>
            nextCursor : {text(history.data?.nextCursor)}
          </Text>
        </View>
        <Button
          label={polling ? 'Arrêter' : 'Suivre 2 s'}
          variant={polling ? 'danger' : 'secondary'}
          size="sm"
          fullWidth={false}
          onPress={() => setPolling((value) => !value)}
          testID="devtools-poll-toggle"
        />
      </View>

      <Spacer size={4} />

      {history.isError && (
        <Text variant="bodySm" color="danger">
          {history.error instanceof Error ? history.error.message : 'Requête en échec'}
        </Text>
      )}

      {items.map((item, index) => {
        const key = `${text(item.reference)}-${index}`;
        return (
          <View key={key} style={styles.card}>
            <Surface elevation={1} padding={4}>
              <Pressable
                onPress={() => setExpanded((current) => (current === key ? null : key))}
                haptic="tap"
                scale="card"
                accessibilityLabel={`Opération ${text(item.reference)}, ${text(item.status)}`}
                testID={`devtools-tx-${text(item.reference)}`}
              >
                <View style={styles.row}>
                  <Text variant="monoMd" numberOfLines={1} style={styles.grow}>
                    {text(item.reference)}
                  </Text>
                  <View
                    style={[
                      styles.stateTag,
                      { backgroundColor: theme.bg.surface3, borderRadius: radius.xs },
                    ]}
                  >
                    <Text variant="labelSm" color="secondary">
                      {text(item.status)}
                    </Text>
                  </View>
                </View>
                <Spacer size={1} />
                <Text variant="bodySm" color="tertiary">
                  {text(item.kind)} · {text(item.amount)} {text(item.currency)} ·{' '}
                  {text(item.counterpart ?? item.channel)}
                </Text>
                <Text variant="bodySm" color="tertiary">
                  {text(item.date)}
                </Text>
              </Pressable>

              {expanded === key && (
                <>
                  <Spacer size={3} />
                  <Button
                    label="Copier le JSON brut"
                    variant="ghost"
                    size="sm"
                    icon="copy"
                    onPress={() => void copy(item, 'JSON')}
                    testID={`devtools-copy-${text(item.reference)}`}
                  />
                </>
              )}
            </Surface>
          </View>
        );
      })}

      {items.length === 0 && !history.isPending && !history.isError && (
        <Text variant="bodySm" color="tertiary">
          Aucune opération renvoyée par le serveur.
        </Text>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingBottom: space[8] },
  toolbar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space[3] },
  toolbarText: { flexShrink: 1, gap: space[1] },
  card: { marginBottom: space[3] },
  row: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
  grow: { flexShrink: 1, flexGrow: 1 },
  stateTag: { paddingHorizontal: space[2], paddingVertical: space[1] },
});
