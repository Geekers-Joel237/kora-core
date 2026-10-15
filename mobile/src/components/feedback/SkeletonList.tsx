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

const styles = StyleSheet.create({
  row: {
    minHeight: layout.rowHeight,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    paddingHorizontal: space[4],
  },
  body: { flex: 1, gap: space[2] },
  trailing: { alignItems: 'flex-end' },
});
