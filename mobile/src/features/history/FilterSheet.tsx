import { ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/action';
import { DateRangePicker, OptionRow, Segmented } from '@/components/input';
import { Sheet } from '@/components/overlay';
import { Divider, Spacer, Text } from '@/components/primitives';
import { outcomeLabel } from '@/features/shared/labels';
import { space, stroke, useTheme } from '@/theme';
import {
  CUSTOMER_STATUSES,
  outcomeOfStatus,
  type CustomerStatus,
  type TxKind,
} from '@/types/domain';
import {
  activeFilterCount,
  NO_FILTERS,
  PERIOD_DAYS,
  PERIOD_PRESETS,
  withPeriod,
  withRange,
  type ActiveFilters,
  type PeriodPreset,
} from './filters';

export interface FilterSheetProps {
  visible: boolean;
  onClose: () => void;
  filters: ActiveFilters;
  onChange: (filters: ActiveFilters) => void;
  /** Taille de la première page pour la sélection courante. */
  resultCount: number | null;
  /** La première page a une suite : le serveur ne donne pas de total. */
  moreResults: boolean;
  loading: boolean;
}

/**
 * Panneau de filtres — `docs/05-screens.md` §5.
 *
 * **Application en direct, sans bouton de validation.** Le pied de feuille
 * annonce le résultat de la sélection courante : c'est ce retour immédiat qui
 * remplace le bouton, et il n'a de sens que si chaque changement relance
 * effectivement la requête.
 *
 * Le statut se filtre par **famille**, comme il s'affiche : le serveur recouvre
 * lui-même tous les états techniques d'une famille. Contrat §3.1.
 */
export function FilterSheet({
  visible,
  onClose,
  filters,
  onChange,
  resultCount,
  moreResults,
  loading,
}: FilterSheetProps) {
  const { t } = useTranslation();
  const theme = useTheme();

  const kindOptions = [
    { value: null, label: t('filters.all') },
    { value: 'DEPOSIT' as TxKind, label: t('filters.deposits') },
    { value: 'WITHDRAWAL' as TxKind, label: t('filters.withdrawals') },
    { value: 'TRANSFER_SENT' as TxKind, label: t('filters.sent') },
    { value: 'TRANSFER_RECEIVED' as TxKind, label: t('filters.received') },
  ];

  const periodOptions: { value: PeriodPreset | 'custom' | null; label: string }[] = [
    { value: null, label: t('filters.anyPeriod') },
    ...PERIOD_PRESETS.map((preset) => ({
      value: preset,
      label: t('filters.days', { count: PERIOD_DAYS[preset] }),
    })),
    { value: 'custom', label: t('filters.custom') },
  ];

  const customRange = filters.from !== null || filters.to !== null;
  const periodValue: PeriodPreset | 'custom' | null = filters.period ?? (customRange ? 'custom' : null);

  const setPeriod = (value: PeriodPreset | 'custom' | null) => {
    if (value === 'custom') {
      onChange(withRange(filters, null, null));
      return;
    }
    onChange(withPeriod(filters, value));
  };

  const setStatus = (status: CustomerStatus) => {
    onChange({ ...filters, status: filters.status === status ? null : status });
  };

  const count = activeFilterCount(filters);

  return (
    <Sheet visible={visible} onClose={onClose} title={t('filters.title')} snapRatio={0.86}>
      <ScrollView
        style={styles.scroll}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.content}
      >
        <Section title={t('filters.type')}>
          <Segmented
            options={kindOptions}
            value={filters.kind}
            onChange={(value) => onChange({ ...filters, kind: value })}
            accessibilityLabel={t('filters.typeA11y')}
            testID="filter-kind"
          />
        </Section>

        <Section title={t('filters.period')}>
          <Segmented
            options={periodOptions}
            value={periodValue}
            onChange={setPeriod}
            accessibilityLabel={t('filters.period')}
            testID="filter-period"
          />
          {periodValue === 'custom' && (
            <>
              <Spacer size={4} />
              <DateRangePicker
                from={filters.from}
                to={filters.to}
                onChange={(from, to) => onChange(withRange(filters, from, to))}
              />
            </>
          )}
        </Section>

        <Section title={t('filters.status')}>
          {CUSTOMER_STATUSES.map((status) => (
            <View key={status}>
              <OptionRow
                label={outcomeLabel(outcomeOfStatus(status))}
                selected={filters.status === status}
                onPress={() => setStatus(status)}
                testID={`filter-status-${status}`}
              />
              <Divider />
            </View>
          ))}
        </Section>
      </ScrollView>

      <View style={[styles.footer, { borderTopColor: theme.overlay.hairline }]}>
        <Text variant="bodySm" color="secondary">
          {loading
            ? t('filters.searching')
            : resultCount === null
              ? '—'
              : moreResults
                ? t('filters.resultCountMore', { count: resultCount })
                : t('filters.resultCount', { count: resultCount })}
        </Text>
        <View style={styles.footerActions}>
          {count > 0 && (
            <Button
              label={t('common.reset')}
              variant="ghost"
              size="sm"
              fullWidth={false}
              onPress={() => onChange(NO_FILTERS)}
              testID="filter-reset"
            />
          )}
          <Button
            label={t('filters.apply')}
            size="sm"
            fullWidth={false}
            onPress={onClose}
            testID="filter-apply"
          />
        </View>
      </View>
    </Sheet>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text variant="labelSm" color="tertiary">
        {title.toUpperCase()}
      </Text>
      <Spacer size={2} />
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingBottom: space[6] },
  section: { paddingBottom: space[6] },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingTop: space[3],
    borderTopWidth: stroke.hairline,
  },
  footerActions: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
});
