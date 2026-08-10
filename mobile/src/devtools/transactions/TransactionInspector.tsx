import { useCallback, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';

import { Button } from '@/components/action';
import { useToast } from '@/components/feedback';
import { Divider, Pressable, Spacer, Surface, Text } from '@/components/primitives';
import { toTransactionPage } from '@/features/shared/mappers';
import { transactionHistorySchema } from '@/features/shared/schemas';
import { decode } from '@/lib/decode';
import { request } from '@/lib/http';
import { radius, space, useTheme } from '@/theme';
import type { Page, Transaction } from '@/types/domain';
import { formatDuration, interStateDurations, totalDuration } from './durations';

/** Sondage forcé, **sans plafond** — `docs/10-validation-mode.md` §4. */
const FORCED_POLL_MS = 2000;
const INSPECTOR_SIZE = 50;

interface RawHistory {
  /** Charge utile telle que reçue, avant toute traduction. */
  raw: unknown;
  page: Page<Transaction>;
}

/**
 * Requête brute — **exception assumée à la règle R1**.
 *
 * Les écrans produit ne voient que des types de domaine. L'inspecteur, lui,
 * existe précisément pour montrer ce que le serveur envoie : il conserve la
 * charge utile intacte à côté de la version traduite, et n'utilise le schéma
 * partagé que pour dériver les durées. Ce module ne quitte jamais
 * `src/devtools/`.
 */
async function fetchRawHistory(): Promise<RawHistory> {
  const raw = await request<unknown>('/payments/history', {
    query: { page: 0, size: INSPECTOR_SIZE, detail: true },
  });
  return { raw, page: toTransactionPage(decode(transactionHistorySchema, raw, '/payments/history')) };
}

function rawItemAt(raw: unknown, index: number): unknown {
  if (typeof raw !== 'object' || raw === null) return null;
  const transactions = (raw as { transactions?: unknown }).transactions;
  return Array.isArray(transactions) ? (transactions[index] ?? null) : null;
}

/**
 * Inspecteur de transactions — `docs/10-validation-mode.md` §4.
 *
 * Vue brute, sans traduction ni regroupement en familles : les onze états
 * s'affichent tels quels. **La colonne des durées inter-états est ce qui
 * transforme cet écran en outil de diagnostic** — elle rend visible que
 * l'autorisation prend 200 ms et la capture 150 ms, conformément à
 * `kora.provider.latency.*`, et fait ressortir toute anomalie immédiatement.
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

  const items = history.data?.page.items ?? [];

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      <View style={styles.toolbar}>
        <View style={styles.toolbarText}>
          <Text variant="labelMd">
            {items.length} opération{items.length > 1 ? 's' : ''} · size={INSPECTOR_SIZE}
          </Text>
          <Text variant="bodySm" color="tertiary">
            {polling ? `suivi forcé toutes les ${FORCED_POLL_MS / 1000} s` : 'suivi à l’arrêt'}
            {history.isFetching ? ' · en cours' : ''}
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

      {items.map((transaction, index) => (
        <View key={transaction.id} style={styles.card}>
          <Surface elevation={1} padding={4}>
            <Pressable
              onPress={() => setExpanded((current) => (current === transaction.id ? null : transaction.id))}
              haptic="tap"
              scale="card"
              accessibilityLabel={`Opération ${transaction.reference}, ${transaction.state}`}
              testID={`devtools-tx-${transaction.id}`}
            >
              <View style={styles.row}>
                <Text variant="monoMd" numberOfLines={1} style={styles.grow}>
                  {transaction.reference}
                </Text>
                <View
                  style={[
                    styles.stateTag,
                    { backgroundColor: theme.bg.surface3, borderRadius: radius.xs },
                  ]}
                >
                  <Text variant="labelSm" color="secondary">
                    {transaction.state}
                  </Text>
                </View>
              </View>
              <Spacer size={1} />
              <Text variant="bodySm" color="tertiary">
                {transaction.type} · {transaction.direction} · {transaction.amount.minor}{' '}
                {transaction.amount.currency} · {transaction.paymentMethod}
              </Text>
              <Text variant="bodySm" color="tertiary">
                {transaction.createdAt.toISOString()}
              </Text>
            </Pressable>

            {expanded === transaction.id && (
              <>
                <Spacer size={3} />
                <Divider />
                <Spacer size={3} />
                <Timeline transaction={transaction} />
                <Spacer size={3} />
                <Button
                  label="Copier le JSON brut"
                  variant="ghost"
                  size="sm"
                  icon="copy"
                  onPress={() => void copy(rawItemAt(history.data?.raw, index), 'JSON')}
                  testID={`devtools-copy-${transaction.id}`}
                />
              </>
            )}
          </Surface>
        </View>
      ))}

      {items.length === 0 && !history.isPending && !history.isError && (
        <Text variant="bodySm" color="tertiary">
          Aucune opération renvoyée par le serveur.
        </Text>
      )}
    </ScrollView>
  );
}

/** Frise brute : instants à la milliseconde et delta avec l'état précédent. */
function Timeline({ transaction }: { transaction: Transaction }) {
  const history = transaction.stateHistory ?? [];

  if (history.length === 0) {
    return (
      <Text variant="bodySm" color="tertiary">
        `stateHistory` absent ou vide malgré `detail=true`.
      </Text>
    );
  }

  const durations = interStateDurations(history);

  return (
    <View>
      {history.map((transition, index) => {
        const delta = index === 0 ? null : durations[index - 1];
        return (
          <View key={`${transition.to}-${transition.occurredAt.getTime()}`} style={styles.timelineRow}>
            <Text variant="monoMd" style={styles.grow} numberOfLines={1}>
              {transition.from ?? '∅'} → {transition.to}
            </Text>
            <Text variant="monoMd" color="tertiary">
              {delta ? `+${formatDuration(delta.ms)}` : '—'}
            </Text>
          </View>
        );
      })}
      <Spacer size={2} />
      <Text variant="bodySm" color="secondary">
        Total {formatDuration(totalDuration(history))} sur {history.length} transitions
      </Text>
    </View>
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
  timelineRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space[3],
    paddingVertical: space[1],
  },
});
