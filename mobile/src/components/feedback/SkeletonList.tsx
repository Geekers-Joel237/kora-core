import { StyleSheet, View } from 'react-native';

import { layout, space } from '@/theme';
import { Skeleton } from './Skeleton';

const BADGE = 44;

/**
 * Squelette d'une liste d'opérations.
 *
 * Reproduit la **forme exacte** de `TransactionRow` — même hauteur de ligne,
 * même pastille, même répartition. C'est ce qui rend l'attente lisible : un
 * bloc gris générique annoncerait un chargement sans annoncer quoi.
 */
export function SkeletonTransactionList({ count = 5 }: { count?: number }) {
  return (
    <View>
      {Array.from({ length: count }, (_, index) => (
        <View key={index} style={styles.row}>
          <Skeleton width={BADGE} height={BADGE} radius="full" />
          <View style={styles.body}>
            <Skeleton width="55%" height={15} />
            <Skeleton width="35%" height={13} />
          </View>
          <View style={styles.trailing}>
            <Skeleton width={80} height={16} />
          </View>
        </View>
      ))}
    </View>
  );
}

const TIMELINE_NODE = 12;
const TIMELINE_SEGMENT = 28;

/**
 * Squelette de la frise d'états.
 *
 * Reprend le rail et les nœuds de `StateTimeline` : l'utilisateur voit qu'une
 * chronologie arrive, pas qu'un bloc quelconque charge. Le nombre de nœuds est
 * une supposition assumée — quatre est la longueur d'un parcours nominal.
 */
export function SkeletonTimeline({ nodes = 4 }: { nodes?: number }) {
  return (
    <View>
      {Array.from({ length: nodes }, (_, index) => (
        <View key={index} style={styles.timelineRow}>
          <View style={styles.rail}>
            <Skeleton width={TIMELINE_NODE} height={TIMELINE_NODE} radius="full" />
            {index < nodes - 1 && <Skeleton width={2} height={TIMELINE_SEGMENT} />}
          </View>
          <View style={styles.timelineBody}>
            <Skeleton width="45%" height={15} />
            <Skeleton width="65%" height={13} />
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    height: layout.rowHeight,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    paddingHorizontal: space[4],
  },
  body: { flex: 1, gap: space[2] },
  trailing: { alignItems: 'flex-end' },
  timelineRow: { flexDirection: 'row', gap: space[3] },
  rail: { alignItems: 'center', width: BADGE / 3, gap: space[1] },
  timelineBody: { flex: 1, gap: space[2], paddingBottom: space[5] },
});
