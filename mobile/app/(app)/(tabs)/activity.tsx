import { useCallback, useMemo, useState } from 'react';
import { RefreshControl, StyleSheet, View } from 'react-native';
import { FlashList } from '@shopify/flash-list';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { router } from 'expo-router';

import { IconButton } from '@/components/action';
import { DayHeader, TransactionRow } from '@/components/display';
import {
  EmptyState,
  ErrorState,
  OfflineBanner,
  SkeletonTransactionList,
} from '@/components/feedback';
import { Divider, Spacer, Text } from '@/components/primitives';
import { FilterSheet } from '@/features/history/FilterSheet';
import {
  activeFilterCount,
  hasAnyFilter,
  NO_FILTERS,
  serializeFilters,
  type ActiveFilters,
} from '@/features/history/filters';
import { toHistoryRows, type HistoryRow } from '@/features/history/grouping';
import { useHistoryInfinite } from '@/features/history/hooks';
import { haptic } from '@/lib/haptics';
import { useNetwork } from '@/lib/network';
import { layout, radius, space, useTheme } from '@/theme';

/** Déclenchement de la page suivante à 80 % — `docs/05-screens.md` §5. */
const END_REACHED_THRESHOLD = 0.2;
const INITIAL_SKELETONS = 8;
const FOOTER_SKELETONS = 3;
const BADGE_SIZE = 18;

export default function ActivityScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const offline = useNetwork((state) => state.offline);

  const [filters, setFilters] = useState<ActiveFilters>(NO_FILTERS);
  const [sheetOpen, setSheetOpen] = useState(false);

  const history = useHistoryInfinite(filters);

  const { rows, stickyIndices } = useMemo(
    () => toHistoryRows(history.data?.pages ?? []),
    [history.data],
  );

  const filterCount = activeFilterCount(filters);
  const filtered = hasAnyFilter(filters);
  const total = history.data?.pages[0]?.totalElements ?? null;

  const openDetail = useCallback(
    (id: string, page: number) => {
      router.push({
        // Faute de `GET /payments/{id}`, la page d'origine et les filtres font
        // partie de l'identité de l'écran de détail. Contrat §6.7.
        pathname: '/transaction/[id]',
        params: { id, page: String(page), filters: serializeFilters(filters) },
      });
    },
    [filters],
  );

  const loadMore = useCallback(() => {
    // Hors ligne, la pagination est coupée : chaque tentative produirait une
    // erreur là où l'utilisateur voit une liste qui fonctionne.
    if (offline || !history.hasNextPage || history.isFetchingNextPage) return;
    void history.fetchNextPage();
  }, [offline, history]);

  const refresh = useCallback(() => {
    haptic.select();
    void history.refetch();
  }, [history]);

  const renderRow = useCallback(
    ({ item }: { item: HistoryRow }) => {
      if (item.kind === 'header') return <DayHeader label={item.label} />;
      return (
        <TransactionRow
          transaction={item.transaction}
          onPress={(tx) => openDetail(tx.id, item.page)}
        />
      );
    },
    [openDetail],
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.bg.app }]}>
      <View style={{ paddingTop: insets.top }}>
        <OfflineBanner />
        <View style={styles.nav}>
          <Text variant="titleLg">Activité</Text>
          <View>
            <IconButton
              name="filter"
              onPress={() => setSheetOpen(true)}
              accessibilityLabel={
                filterCount > 0 ? `Filtres, ${filterCount} actifs` : 'Filtrer l’historique'
              }
              tint={filterCount > 0 ? theme.accent.primary : undefined}
              testID="open-filters"
            />
            {filterCount > 0 && (
              <View
                style={[
                  styles.badge,
                  { backgroundColor: theme.accent.primary, borderRadius: radius.full },
                ]}
                pointerEvents="none"
              >
                <Text variant="labelSm" color="onAccent" align="center">
                  {filterCount}
                </Text>
              </View>
            )}
          </View>
        </View>
      </View>

      <Body />

      <FilterSheet
        visible={sheetOpen}
        onClose={() => setSheetOpen(false)}
        filters={filters}
        onChange={setFilters}
        resultCount={total}
        loading={history.isFetching && !history.isFetchingNextPage}
      />
    </View>
  );

  function Body() {
    if (history.isPending) {
      return (
        <View style={styles.padded}>
          <Spacer size={4} />
          <SkeletonTransactionList count={INITIAL_SKELETONS} />
        </View>
      );
    }

    if (history.isError && rows.length === 0) {
      return (
        <ErrorState
          title="Historique indisponible"
          description="Nous n’avons pas pu charger vos opérations."
          onRetry={() => void history.refetch()}
          error={history.error}
        />
      );
    }

    if (rows.length === 0) {
      return filtered ? (
        <EmptyState
          icon="search"
          title="Aucun résultat"
          description="Aucune opération ne correspond à ces filtres."
          actionLabel="Réinitialiser les filtres"
          onAction={() => setFilters(NO_FILTERS)}
        />
      ) : (
        <EmptyState
          icon="activity"
          title="Aucune opération"
          description="Votre première opération apparaîtra ici."
          actionLabel="Faire un dépôt"
          onAction={() => router.push('/deposit')}
        />
      );
    }

    return (
      <FlashList
        data={rows}
        renderItem={renderRow}
        keyExtractor={(item) => item.key}
        // Deux gabarits de hauteurs très différentes : sans cette distinction,
        // la virtualisation recycle un en-tête en ligne et inversement.
        getItemType={(item) => item.kind}
        stickyHeaderIndices={stickyIndices}
        ItemSeparatorComponent={Separator}
        onEndReached={loadMore}
        onEndReachedThreshold={END_REACHED_THRESHOLD}
        ListFooterComponent={
          history.isFetchingNextPage ? (
            <SkeletonTransactionList count={FOOTER_SKELETONS} />
          ) : null
        }
        contentContainerStyle={{ paddingBottom: insets.bottom + layout.anchoredBarClearance }}
        refreshControl={
          <RefreshControl
            refreshing={history.isRefetching && !history.isFetchingNextPage}
            onRefresh={refresh}
            tintColor={theme.accent.primary}
            colors={[theme.accent.primary]}
            progressBackgroundColor={theme.bg.surface2}
          />
        }
        testID="history-list"
      />
    );
  }
}

/** Aucun filet sous une ligne suivie d'un en-tête : l'en-tête sépare déjà. */
function Separator({ leadingItem }: { leadingItem?: HistoryRow }) {
  if (!leadingItem || leadingItem.kind === 'header') return null;
  return <Divider inset={4} />;
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  nav: {
    height: layout.navBarHeight,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space[5],
  },
  padded: { paddingHorizontal: space[1] },
  badge: {
    position: 'absolute',
    top: 0,
    right: 0,
    minWidth: BADGE_SIZE,
    height: BADGE_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: space[1],
  },
});