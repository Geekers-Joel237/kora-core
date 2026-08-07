import { useCallback, useEffect } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { router, useLocalSearchParams } from 'expo-router';
import * as Clipboard from 'expo-clipboard';

import { Button, IconButton } from '@/components/action';
import { StateTimeline, StatusChip } from '@/components/display';
import { ErrorState, SkeletonTimeline, useToast } from '@/components/feedback';
import { Amount } from '@/components/money';
import { Divider, Icon, Pressable, Spacer, Surface, Text, type IconName } from '@/components/primitives';
import { parseFilters } from '@/features/history/filters';
import { useCachedTransaction, useTransactionDetail } from '@/features/history/hooks';
import { paymentMethodLabel, transactionTypeLabel } from '@/features/shared/labels';
import { formatDetailTimestamp } from '@/lib/datetime';
import { layout, radius, space, spring, timing, useReduceMotion, useTheme } from '@/theme';
import { isTerminalState, type Transaction } from '@/types/domain';

const TYPE_ICONS: Record<string, IconName> = {
  CASH_IN: 'arrow-down-circle',
  CASH_OUT: 'arrow-up-circle',
  P2P_TRANSFER: 'send',
};

const STAGGER_MS = 60;
const REVEAL_DISTANCE = 12;

/**
 * Détail d'une opération — `docs/05-screens.md` §6.
 *
 * **L'écran qui matérialise le différenciateur produit** : le backend horodate
 * chaque transition d'état, et Kora est l'application qui les montre.
 *
 * Deux sources se superposent : le cache de la liste, qui donne montant,
 * contrepartie et date **immédiatement**, et le rejeu de la page d'origine avec
 * `detail=true`, qui apporte l'historique d'états. L'écran ne reste donc jamais
 * vide, même quand la requête met une seconde.
 */
export default function TransactionDetailScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const reduceMotion = useReduceMotion();

  const params = useLocalSearchParams<{ id: string; page?: string; filters?: string }>();
  const id = params.id;
  const page = Number.parseInt(params.page ?? '0', 10);
  const filters = parseFilters(params.filters);

  const cached = useCachedTransaction(id);
  const detail = useTransactionDetail(id, Number.isNaN(page) ? 0 : page, filters);

  // Le détail fait autorité dès qu'il arrive : son état est plus récent que
  // celui du cache de liste, qui peut dater de plusieurs minutes.
  const transaction: Transaction | null = detail.data ?? cached;

  const copyReference = useCallback(async () => {
    if (!transaction) return;
    await Clipboard.setStringAsync(transaction.reference);
    toast.show({ message: 'Référence copiée', icon: 'copy', tone: 'success' });
  }, [transaction, toast]);

  const notFound = detail.isSuccess && detail.data === null && cached === null;

  return (
    <View style={[styles.container, { backgroundColor: theme.bg.app }]}>
      <View style={[styles.nav, { paddingTop: insets.top }]}>
        <IconButton
          name="back"
          onPress={() => router.back()}
          accessibilityLabel="Retour"
          testID="detail-back"
        />
      </View>

      {notFound ? (
        <ErrorState
          title="Opération introuvable"
          description="Elle n’apparaît plus dans la page d’où elle a été ouverte."
          onRetry={() => router.back()}
          retryLabel="Retour à l’historique"
        />
      ) : detail.isError && !transaction ? (
        <ErrorState
          title="Détail indisponible"
          description="Nous n’avons pas pu récupérer cette opération."
          onRetry={() => void detail.refetch()}
          error={detail.error}
        />
      ) : !transaction ? (
        <View style={styles.content}>
          <Spacer size={8} />
          <SkeletonTimeline />
        </View>
      ) : (
        <ScrollView
          contentContainerStyle={[
            styles.content,
            { paddingBottom: insets.bottom + layout.anchoredBarClearance },
          ]}
          showsVerticalScrollIndicator={false}
        >
          <Reveal index={0} reduceMotion={reduceMotion}>
            <View style={styles.hero}>
              <View
                style={[
                  styles.badge,
                  {
                    backgroundColor:
                      transaction.outcome === 'success'
                        ? theme.bg.surface2
                        : theme.status[transaction.outcome].bg,
                    borderRadius: radius.full,
                  },
                ]}
              >
                <Icon
                  name={TYPE_ICONS[transaction.type] ?? 'more-horizontal'}
                  size="lg"
                  color={
                    transaction.outcome === 'success'
                      ? theme.text.secondary
                      : theme.status[transaction.outcome].fg
                  }
                />
              </View>

              <Spacer size={5} />
              <Amount
                minor={transaction.amount.minor}
                currency={transaction.amount.currency}
                size="displayMd"
                sign={transaction.direction === 'INBOUND' ? 'always' : 'never'}
                direction={transaction.direction}
                align="center"
                testID="detail-amount"
              />
              <Spacer size={2} />
              <Text variant="bodyMd" color="secondary" align="center">
                {transaction.counterpart ?? paymentMethodLabel(transaction.paymentMethod)}
              </Text>
              <Spacer size={1} />
              <Text variant="bodySm" color="tertiary" align="center">
                {formatDetailTimestamp(transaction.createdAt)}
              </Text>

              {transaction.outcome !== 'success' && (
                <>
                  <Spacer size={4} />
                  <StatusChip state={transaction.state} size="md" showIcon detailed />
                </>
              )}
            </View>
          </Reveal>

          <Spacer size={8} />

          <Reveal index={1} reduceMotion={reduceMotion}>
            <Surface elevation={1} padding={5}>
              {transaction.stateHistory && transaction.stateHistory.length > 0 ? (
                <StateTimeline
                  history={transaction.stateHistory}
                  currentState={transaction.state}
                />
              ) : detail.isFetching ? (
                <SkeletonTimeline />
              ) : (
                <Text variant="bodySm" color="tertiary">
                  Aucun historique d’état n’a été renvoyé pour cette opération.
                </Text>
              )}

              {!isTerminalState(transaction.state) && (
                <>
                  <Spacer size={2} />
                  <Text variant="bodySm" color="tertiary">
                    Suivi en direct — cet écran se met à jour tout seul.
                  </Text>
                </>
              )}
            </Surface>
          </Reveal>

          <Spacer size={5} />

          <Reveal index={2} reduceMotion={reduceMotion}>
            <Surface elevation={1} padding={0}>
              <Pressable
                onLongPress={() => void copyReference()}
                onPress={() => void copyReference()}
                haptic="tap"
                scale="card"
                accessibilityLabel={`Référence ${transaction.reference}, appuyer pour copier`}
                testID="copy-reference"
              >
                <DetailRow label="Réf." value={transaction.reference} mono trailingIcon="copy" />
              </Pressable>
              <Divider inset={4} />
              <DetailRow label="Type" value={transactionTypeLabel(transaction.type)} />
              <Divider inset={4} />
              <DetailRow label="Moyen" value={paymentMethodLabel(transaction.paymentMethod)} />
              <Divider inset={4} />
              <DetailRow
                label="Sens"
                value={transaction.direction === 'INBOUND' ? 'Entrant' : 'Sortant'}
              />
              <Divider inset={4} />
              <DetailRow label="État brut" value={transaction.state} mono />
            </Surface>
          </Reveal>

          <Spacer size={6} />

          <Reveal index={3} reduceMotion={reduceMotion}>
            <Button
              label="Copier la référence"
              variant="secondary"
              icon="copy"
              onPress={() => void copyReference()}
              testID="detail-copy-button"
            />
          </Reveal>
        </ScrollView>
      )}
    </View>
  );
}

function DetailRow({
  label,
  value,
  mono = false,
  trailingIcon,
}: {
  label: string;
  value: string;
  mono?: boolean;
  trailingIcon?: IconName;
}) {
  const theme = useTheme();

  return (
    <View style={styles.detailRow}>
      <Text variant="bodyMd" color="secondary">
        {label}
      </Text>
      <View style={styles.detailValue}>
        <Text variant={mono ? 'monoMd' : 'bodyMd'} numberOfLines={1}>
          {value}
        </Text>
        {trailingIcon && <Icon name={trailingIcon} size="xs" color={theme.text.tertiary} />}
      </View>
    </View>
  );
}

/**
 * Entrée en cascade — repli **imposé** de la transition partagée.
 *
 * Reanimated 4 garde les transitions d'élément partagé derrière le drapeau
 * statique `ENABLE_SHARED_ELEMENT_TRANSITIONS`, à `false` par défaut
 * (`react-native-reanimated/src/featureFlags/staticFeatureFlags`), et elles
 * restent expérimentales sur la nouvelle architecture — la seule que
 * Reanimated 4 supporte. Les activer exigerait une reconstruction native pour
 * un mécanisme non garanti sur un écran central.
 *
 * Le repli retenu n'est pas une absence d'animation : chaque bloc monte de
 * quelques points avec 60 ms de décalage, ce qui donne la même impression de
 * continuité sans dépendre d'une fonctionnalité désactivée.
 *
 * `CONTOURNEMENT(indéterminé)` — à revoir si le drapeau devient stable.
 */
function Reveal({
  index,
  reduceMotion,
  children,
}: {
  index: number;
  reduceMotion: boolean;
  children: React.ReactNode;
}) {
  const progress = useSharedValue(0);

  useEffect(() => {
    if (reduceMotion) {
      progress.value = withTiming(1, { duration: timing.fast });
      return;
    }
    progress.value = withDelay(index * STAGGER_MS, withSpring(1, spring.gentle));
  }, [index, reduceMotion, progress]);

  const style = useAnimatedStyle(() => ({
    opacity: progress.value,
    transform: [{ translateY: (1 - progress.value) * REVEAL_DISTANCE }],
  }));

  return <Animated.View style={style}>{children}</Animated.View>;
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  nav: { paddingHorizontal: space[2] },
  content: { paddingHorizontal: space[5] },
  hero: { alignItems: 'center' },
  badge: {
    width: layout.iconBadgeLarge,
    height: layout.iconBadgeLarge,
    alignItems: 'center',
    justifyContent: 'center',
  },
  detailRow: {
    minHeight: layout.minTouchTarget,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space[4],
    paddingHorizontal: space[4],
    paddingVertical: space[3],
  },
  detailValue: { flexDirection: 'row', alignItems: 'center', gap: space[2], flexShrink: 1 },
});