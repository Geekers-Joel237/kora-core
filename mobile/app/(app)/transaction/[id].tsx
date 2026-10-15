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
import { useTranslation } from 'react-i18next';
import { router, useLocalSearchParams } from 'expo-router';
import * as Clipboard from 'expo-clipboard';

import { Button, IconButton } from '@/components/action';
import { StatusChip } from '@/components/display';
import {
  ErrorState,
  SkeletonTransactionList,
  useDelayedLoading,
  useToast,
} from '@/components/feedback';
import { Amount } from '@/components/money';
import { Divider, Icon, Pressable, Spacer, Surface, Text, type IconName } from '@/components/primitives';
import { parseFilters } from '@/features/history/filters';
import { useCachedTransaction, useTransactionDetail } from '@/features/history/hooks';
import { kindLabel, paymentMethodLabel } from '@/features/shared/labels';
import { formatDetailTimestamp } from '@/lib/datetime';
import { layout, radius, space, spring, timing, useReduceMotion, useTheme } from '@/theme';
import type { Transaction } from '@/types/domain';

const KIND_ICONS: Record<string, IconName> = {
  DEPOSIT: 'arrow-down-circle',
  WITHDRAWAL: 'arrow-up-circle',
  TRANSFER_SENT: 'send',
  TRANSFER_RECEIVED: 'arrow-down-circle',
};

const STAGGER_MS = 60;
const REVEAL_DISTANCE = 12;

/**
 * Détail d'une opération — `docs/05-screens.md` §6.
 *
 * Une ligne d'historique porte déjà tout ce que cet écran affiche : il s'ouvre
 * **depuis le cache de la liste**, sans requête. Le réseau n'est sollicité que
 * pour suivre une opération encore en cours, ou si le cache a été purgé — par
 * rejeu de la page d'origine, contrat §6.7.
 */
export default function TransactionDetailScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const reduceMotion = useReduceMotion();

  const params = useLocalSearchParams<{ id: string; cursor?: string; filters?: string }>();
  const reference = params.id;
  const cursor = params.cursor ? params.cursor : null;
  const filters = parseFilters(params.filters);

  const cached = useCachedTransaction(reference);
  const detail = useTransactionDetail(
    reference,
    cursor,
    filters,
    cached === null || cached.outcome === 'pending',
  );

  // Le rejeu fait autorité dès qu'il arrive : son statut est plus récent que
  // celui du cache de liste, qui peut dater de plusieurs minutes.
  const transaction: Transaction | null = detail.data ?? cached;

  const copyReference = useCallback(async () => {
    if (!transaction) return;
    await Clipboard.setStringAsync(transaction.reference);
    toast.show({ message: t('detail.referenceCopied'), icon: 'copy', tone: 'success' });
  }, [transaction, toast, t]);

  const notFound = detail.isSuccess && detail.data === null && cached === null;
  // §6.5 — le squelette n'a de sens que si la requête dépasse 200 ms.
  const showSkeleton = useDelayedLoading(transaction === null);

  return (
    <View style={[styles.container, { backgroundColor: theme.bg.app }]}>
      <View style={[styles.nav, { paddingTop: insets.top }]}>
        <IconButton
          name="back"
          onPress={() => router.back()}
          accessibilityLabel={t('common.back')}
          testID="detail-back"
        />
      </View>

      {notFound ? (
        <ErrorState
          title={t('detail.notFoundTitle')}
          description={t('detail.notFoundDescription')}
          onRetry={() => router.back()}
          retryLabel={t('detail.backToHistory')}
        />
      ) : detail.isError && !transaction ? (
        <ErrorState
          title={t('detail.errorTitle')}
          description={t('detail.errorDescription')}
          onRetry={() => void detail.refetch()}
          error={detail.error}
        />
      ) : !transaction ? (
        <View style={styles.content}>
          {showSkeleton && (
            <>
              <Spacer size={8} />
              <SkeletonTransactionList count={3} />
            </>
          )}
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
                  name={KIND_ICONS[transaction.kind] ?? 'more-horizontal'}
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
                {transaction.counterpart ??
                  (transaction.channel
                    ? paymentMethodLabel(transaction.channel)
                    : kindLabel(transaction.kind))}
              </Text>
              <Spacer size={1} />
              <Text variant="bodySm" color="tertiary" align="center">
                {formatDetailTimestamp(transaction.createdAt)}
              </Text>

              {transaction.outcome !== 'success' && (
                <>
                  <Spacer size={4} />
                  <StatusChip outcome={transaction.outcome} size="md" showIcon />
                </>
              )}

              {transaction.outcome === 'pending' && (
                <>
                  <Spacer size={2} />
                  <Text variant="bodySm" color="tertiary" align="center">
                    {t('detail.liveTracking')}
                  </Text>
                </>
              )}
            </View>
          </Reveal>

          <Spacer size={8} />

          <Reveal index={1} reduceMotion={reduceMotion}>
            <Surface elevation={1} padding={0}>
              <Pressable
                onLongPress={() => void copyReference()}
                onPress={() => void copyReference()}
                // §3 — « Copie du numéro d'opération » appelle `select`.
                haptic="select"
                scale="card"
                accessibilityLabel={t('detail.referenceA11y', {
                  reference: transaction.reference,
                })}
                testID="copy-reference"
              >
                <DetailRow
                  label={t('detail.reference')}
                  value={transaction.reference}
                  mono
                  trailingIcon="copy"
                />
              </Pressable>
              <Divider inset={4} />
              <DetailRow label={t('detail.type')} value={kindLabel(transaction.kind)} />
              {transaction.channel && (
                <>
                  <Divider inset={4} />
                  <DetailRow
                    label={t('detail.method')}
                    value={paymentMethodLabel(transaction.channel)}
                  />
                </>
              )}
            </Surface>
          </Reveal>

          <Spacer size={6} />

          <Reveal index={2} reduceMotion={reduceMotion}>
            <Button
              label={t('detail.copyReference')}
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
