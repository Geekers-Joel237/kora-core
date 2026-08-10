import { useCallback } from 'react';
import { RefreshControl, StyleSheet, View } from 'react-native';
import Animated, {
  Extrapolation,
  interpolate,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';
import { router } from 'expo-router';

import { ActionTile, IconButton } from '@/components/action';
import { TransactionRow } from '@/components/display';
import {
  EmptyState,
  ErrorState,
  OfflineBanner,
  SkeletonTransactionList,
  useDelayedLoading,
  useToast,
} from '@/components/feedback';
import { Amount, BalanceHero } from '@/components/money';
import { Divider, Pressable, Spacer, Text } from '@/components/primitives';
import { devtoolsTrigger } from '@/devtools';
import { useSession } from '@/features/auth/session';
import { useRecentTransactions } from '@/features/history/hooks';
import { useBalance } from '@/features/wallet/hooks';
import { formatRelativeAge } from '@/lib/datetime';
import { haptic } from '@/lib/haptics';
import { useNetwork } from '@/lib/network';
import { setBalanceHidden, usePreferences } from '@/lib/preferences';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { layout, space, useTheme } from '@/theme';

/** Amplitude de la compression de la carte héros — §6.6. */
const COLLAPSE_DISTANCE = 120;
const COMPACT_APPEAR_AT = 60;

export default function HomeScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const profile = useSession((state) => state.profile);
  const user = useSession((state) => state.user);
  const offline = useNetwork((state) => state.offline);

  // Deux requêtes **indépendantes** : un échec de l'une ne dégrade jamais
  // l'autre section. Voir `docs/05-screens.md` §3, règle d'isolation.
  const balance = useBalance();
  const history = useRecentTransactions();

  // §6.5 — un squelette n'apparaît qu'au-delà de 200 ms d'attente.
  const showBalanceSkeleton = useDelayedLoading(balance.isPending);
  const showHistorySkeleton = useDelayedLoading(history.isPending);

  // Partagé avec les réglages : les deux commandes doivent rester d'accord.
  const hidden = usePreferences((state) => state.balanceHidden);

  const scrollY = useSharedValue(0);
  const onScroll = useAnimatedScrollHandler((event) => {
    scrollY.value = event.contentOffset.y;
  });

  /** Compression entièrement sur le thread UI — aucun `onScroll` JavaScript. */
  const heroStyle = useAnimatedStyle(() => ({
    opacity: interpolate(scrollY.value, [0, COLLAPSE_DISTANCE], [1, 0], Extrapolation.CLAMP),
    transform: [
      {
        scale: interpolate(scrollY.value, [0, COLLAPSE_DISTANCE], [1, 0.92], Extrapolation.CLAMP),
      },
    ],
  }));

  const compactStyle = useAnimatedStyle(() => ({
    opacity: interpolate(
      scrollY.value,
      [COMPACT_APPEAR_AT, COLLAPSE_DISTANCE],
      [0, 1],
      Extrapolation.CLAMP,
    ),
    transform: [
      {
        translateY: interpolate(
          scrollY.value,
          [COMPACT_APPEAR_AT, COLLAPSE_DISTANCE],
          [8, 0],
          Extrapolation.CLAMP,
        ),
      },
    ],
  }));

  const toggleHidden = useCallback(() => {
    setBalanceHidden(!hidden);
  }, [hidden]);

  const refresh = useCallback(() => {
    haptic.select();
    void balance.refetch();
    void history.refetch();
  }, [balance, history]);

  const copyAccountNumber = useCallback(() => {
    toast.show({ message: t('home.accountNumberCopied'), icon: 'copy', tone: 'success' });
  }, [toast, t]);

  const account = balance.data;
  const currency = account?.balance.currency ?? DEFAULT_CURRENCY;
  const refreshing = balance.isRefetching || history.isRefetching;

  // « Mis à jour il y a X » : le cache reste servi hors ligne (NFR-31), mais
  // l'utilisateur doit savoir que le chiffre n'est peut-être plus à jour.
  const staleLabel =
    offline && balance.dataUpdatedAt > 0
      ? t('home.updatedAt', { age: formatRelativeAge(new Date(balance.dataUpdatedAt)) })
      : null;

  return (
    <View style={[styles.container, { backgroundColor: theme.bg.app }]}>
      <View style={{ paddingTop: insets.top }}>
        <OfflineBanner />
        <View style={styles.nav}>
          <View style={styles.navTitle}>
            {/* Déclencheur du mode validation — §2. Inerte en production :
                `devtoolsTrigger` y est une paire de fonctions vides. */}
            <Pressable
              onLongPress={devtoolsTrigger.logoLongPress}
              haptic="none"
              scale="hero"
              accessibilityLabel={t('home.greetingA11y')}
            >
              <Text variant="titleSm" numberOfLines={1}>
                {t('home.greeting', {
                  // Contrat §6.3 — sur un nouvel appareil, le nom est inconnu :
                  // repli sur l'e-mail, jamais sur un nom fabriqué.
                  name:
                    profile.fullName?.split(' ')[0] ?? user?.email ?? t('home.greetingFallback'),
                })}
              </Text>
            </Pressable>
            <Animated.View style={compactStyle}>
              {account && (
                <Amount
                  minor={account.balance.minor}
                  currency={account.balance.currency}
                  size="bodyMd"
                  hidden={hidden}
                />
              )}
            </Animated.View>
          </View>
          <IconButton
            name="settings"
            onPress={() => router.push('/settings')}
            accessibilityLabel={t('tabs.settings')}
            testID="open-settings"
          />
        </View>
      </View>

      <Animated.ScrollView
        onScroll={onScroll}
        scrollEventThrottle={16}
        contentContainerStyle={[
          styles.content,
          { paddingBottom: insets.bottom + layout.anchoredBarClearance },
        ]}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={theme.accent.primary}
            colors={[theme.accent.primary]}
            progressBackgroundColor={theme.bg.surface2}
          />
        }
      >
        <Animated.View style={heroStyle}>
          {balance.isError && !account ? (
            <ErrorState
              title={t('home.balanceErrorTitle')}
              description={t('home.balanceErrorDescription')}
              onRetry={() => void balance.refetch()}
              error={balance.error}
              compact
            />
          ) : (
            <BalanceHero
              minor={account?.balance.minor ?? 0}
              currency={currency}
              accountNumber={account?.number ?? ''}
              hidden={hidden}
              onToggleHidden={toggleHidden}
              onCopyAccountNumber={copyAccountNumber}
              loading={showBalanceSkeleton}
              staleLabel={staleLabel}
            />
          )}
        </Animated.View>

        <Spacer size={6} />

        <View style={styles.actions}>
          <ActionTile
            icon="arrow-down-circle"
            label={t('home.deposit')}
            index={0}
            disabled={offline}
            onPress={() => router.push('/deposit')}
            testID="action-deposit"
          />
          <ActionTile
            icon="arrow-up-circle"
            label={t('home.withdraw')}
            index={1}
            disabled={offline}
            onPress={() => router.push('/withdraw')}
            testID="action-withdraw"
          />
          <ActionTile
            icon="send"
            label={t('home.send')}
            index={2}
            disabled={offline}
            onPress={() => router.push('/send')}
            testID="action-send"
          />
        </View>

        {offline && (
          <>
            <Spacer size={3} />
            <Text variant="bodySm" color="tertiary" align="center">
              {t('home.offlineActions')}
            </Text>
          </>
        )}

        <Spacer size={8} />

        <View style={styles.sectionHeader}>
          <Text variant="titleSm">{t('home.recentActivity')}</Text>
          <Pressable
            onPress={() => router.push('/activity')}
            haptic="tap"
            scale="card"
            accessibilityLabel={t('home.seeAllA11y')}
            testID="see-all"
          >
            <Text variant="labelMd" color="accent">
              {t('home.seeAll')}
            </Text>
          </Pressable>
        </View>

        <Spacer size={3} />

        <RecentActivity />
      </Animated.ScrollView>
    </View>
  );

  /** Isolé : sa frontière d'erreur est distincte de celle du solde. */
  function RecentActivity() {
    if (history.isPending)
      return showHistorySkeleton ? <SkeletonTransactionList count={5} /> : null;

    if (history.isError) {
      return (
        <ErrorState
          title={t('home.activityErrorTitle')}
          description={t('home.activityErrorDescription')}
          onRetry={() => void history.refetch()}
          error={history.error}
          compact
        />
      );
    }

    const items = history.data?.items ?? [];

    if (items.length === 0) {
      return (
        <EmptyState
          icon="activity"
          title={t('home.emptyTitle')}
          description={t('home.emptyDescription')}
          actionLabel={t('home.emptyAction')}
          onAction={() => router.push('/deposit')}
          compact
        />
      );
    }

    return (
      <View>
        {items.map((transaction, index) => (
          <View key={transaction.id}>
            {index > 0 && <Divider inset={4} />}
            <TransactionRow
              transaction={transaction}
              onPress={(tx) => router.push(`/transaction/${tx.id}`)}
            />
          </View>
        ))}
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  nav: {
    minHeight: layout.navBarHeight,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space[5],
  },
  navTitle: { flex: 1, gap: space[0] },
  content: { paddingHorizontal: space[5], paddingTop: space[2] },
  actions: { flexDirection: 'row', gap: space[3] },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
});
