import { StyleSheet, View } from 'react-native';

import { useTranslation } from 'react-i18next';

import { Amount } from '@/components/money/Amount';
import { Icon, Pressable, Text, type IconName } from '@/components/primitives';
import { formatRowTimestamp } from '@/lib/datetime';
import { outcomeLabel, paymentMethodLabel, transactionTypeLabel } from '@/features/shared/labels';
import { layout, radius, space, useTheme } from '@/theme';
import type { Transaction } from '@/types/domain';

export interface TransactionRowProps {
  transaction: Transaction;
  onPress?: (transaction: Transaction) => void;
}

const TYPE_ICONS: Record<string, IconName> = {
  CASH_IN: 'arrow-down-circle',
  CASH_OUT: 'arrow-up-circle',
  P2P_TRANSFER: 'send',
};

const BADGE_SIZE = 44;

/**
 * Ligne d'historique — hauteur fixe de 68 dp, virtualisée.
 *
 * **Un état de succès n'affiche aucune puce.** Marquer « Terminé » sur 95 % des
 * lignes revient à n'afficher aucune information : seules les anomalies méritent
 * un marqueur. C'est ce qui rend un historique lisible en un coup d'œil.
 * Voir `docs/04-components.md` §5.
 */
export function TransactionRow({ transaction, onPress }: TransactionRowProps) {
  const theme = useTheme();
  useTranslation();
  const status = theme.status[transaction.outcome];

  // Le serveur masque déjà le numéro pour les P2P ; il vaut `null` pour les
  // dépôts et retraits, dont la contrepartie est le compte de flottement.
  const title = transaction.counterpart ?? paymentMethodLabel(transaction.paymentMethod);
  const subtitle = `${transactionTypeLabel(transaction.type)} · ${formatRowTimestamp(
    transaction.createdAt,
  )}`;

  const content = (
    <View style={styles.row}>
      <View
        style={[
          styles.badge,
          {
            backgroundColor:
              transaction.outcome === 'success' ? theme.bg.surface2 : status.bg,
            borderRadius: radius.full,
          },
        ]}
      >
        <Icon
          name={TYPE_ICONS[transaction.type] ?? 'more-horizontal'}
          size="sm"
          color={transaction.outcome === 'success' ? theme.text.secondary : status.fg}
        />
      </View>

      <View style={styles.body}>
        <Text variant="bodyMd" numberOfLines={1}>
          {title}
        </Text>
        <Text variant="bodySm" color="tertiary" numberOfLines={1}>
          {subtitle}
        </Text>
      </View>

      <View style={styles.trailing}>
        <Amount
          minor={transaction.amount.minor}
          currency={transaction.amount.currency}
          size="bodyLg"
          sign={transaction.direction === 'INBOUND' ? 'always' : 'never'}
          direction={transaction.direction}
          align="right"
        />
        {transaction.outcome !== 'success' && (
          <Text variant="labelSm" tint={status.fg}>
            {outcomeLabel(transaction.outcome)}
          </Text>
        )}
      </View>
    </View>
  );

  if (!onPress) return content;

  return (
    <Pressable
      onPress={() => onPress(transaction)}
      scale="card"
      haptic="tap"
      accessibilityLabel={`${title}, ${subtitle}`}
      testID={`tx-${transaction.id}`}
    >
      {content}
    </Pressable>
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
  badge: {
    width: BADGE_SIZE,
    height: BADGE_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  body: { flex: 1, gap: space[1] },
  trailing: { alignItems: 'flex-end', gap: space[1] },
});
