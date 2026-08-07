import { StyleSheet, View } from 'react-native';

import { IconButton } from '@/components/action';
import { Skeleton } from '@/components/feedback';
import { Pressable, Spacer, Surface, Text } from '@/components/primitives';
import { space, useTheme } from '@/theme';
import { Amount } from './Amount';

export interface BalanceHeroProps {
  minor: number;
  currency: string;
  accountNumber: string;
  hidden: boolean;
  onToggleHidden: () => void;
  onCopyAccountNumber?: () => void;
  loading?: boolean;
  /** « Mis à jour il y a X » quand les données viennent du cache hors ligne. */
  staleLabel?: string | null;
}

/**
 * Carte de solde de l'accueil — `docs/04-components.md` §3.
 *
 * Le montant est animé : après une opération réussie, il part de la **valeur
 * précédente** et non de zéro. C'est la différence entre un chiffre qui se met
 * à jour et une transaction qui se ressent (§6.1).
 */
export function BalanceHero({
  minor,
  currency,
  accountNumber,
  hidden,
  onToggleHidden,
  onCopyAccountNumber,
  loading = false,
  staleLabel = null,
}: BalanceHeroProps) {
  const theme = useTheme();

  return (
    <Surface elevation={1} radius="xl" padding={6}>
      <View style={styles.header}>
        <Text variant="labelMd" color="secondary">
          Solde disponible
        </Text>
        <IconButton
          name={hidden ? 'eye-off' : 'eye'}
          onPress={onToggleHidden}
          haptic="select"
          accessibilityLabel={hidden ? 'Afficher le solde' : 'Masquer le solde'}
          testID="toggle-balance"
        />
      </View>

      <Spacer size={2} />

      {loading ? (
        // Squelette de forme identique au contenu final — jamais un indicateur.
        <View style={styles.skeleton}>
          <Skeleton height={56} width="70%" radius="md" />
          <Skeleton height={18} width="45%" />
        </View>
      ) : (
        <>
          <Amount
            minor={minor}
            currency={currency}
            size="displayXl"
            hidden={hidden}
            animate={!hidden}
            testID="balance-amount"
          />

          <Spacer size={2} />

          <Pressable
            onPress={() => undefined}
            {...(onCopyAccountNumber && { onLongPress: onCopyAccountNumber })}
            haptic="none"
            scale="hero"
            accessibilityLabel={`Numéro de compte ${accountNumber}`}
          >
            <Text variant="monoMd" color="tertiary">
              {accountNumber}
            </Text>
          </Pressable>
        </>
      )}

      {staleLabel && (
        <>
          <Spacer size={2} />
          <Text variant="bodySm" tint={theme.status.pending.fg}>
            {staleLabel}
          </Text>
        </>
      )}
    </Surface>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  skeleton: { gap: space[3] },
});
