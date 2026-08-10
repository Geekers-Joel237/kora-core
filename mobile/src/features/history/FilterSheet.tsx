import { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/action';
import { DateRangePicker, OptionRow, Segmented } from '@/components/input';
import { Sheet } from '@/components/overlay';
import { Divider, Pressable, Spacer, Text } from '@/components/primitives';
import { stateLabel } from '@/features/shared/labels';
import { space, stroke, useTheme } from '@/theme';
import { TX_STATES, type Direction, type TxState, type TxType } from '@/types/domain';
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
  /** Total renvoyé par le serveur pour la sélection courante. */
  resultCount: number | null;
  loading: boolean;
}

/** Nombre d'états visibles avant dépliage. */
const COLLAPSED_STATES = 4;

/**
 * Panneau de filtres — `docs/05-screens.md` §5.
 *
 * **Application en direct, sans bouton de validation.** Le pied de feuille
 * affiche le nombre de résultats de la sélection courante : c'est ce retour
 * immédiat qui remplace le bouton, et il n'a de sens que si chaque changement
 * relance effectivement la requête.
 *
 * Le filtre d'état est un **choix unique parmi les onze états concrets** —
 * contrat §6.8. Les familles restent le mode d'affichage, jamais un filtre :
 * `TransactionFilter.state` n'accepte qu'une valeur, et filtrer localement une
 * famille fausserait le total comme la pagination.
 */
export function FilterSheet({
  visible,
  onClose,
  filters,
  onChange,
  resultCount,
  loading,
}: FilterSheetProps) {
  const { t } = useTranslation();
  const theme = useTheme();
  const [statesExpanded, setStatesExpanded] = useState(false);

  const typeOptions = [
    { value: null, label: t('filters.all') },
    { value: 'CASH_IN' as TxType, label: t('filters.deposits') },
    { value: 'CASH_OUT' as TxType, label: t('filters.withdrawals') },
    { value: 'P2P_TRANSFER' as TxType, label: t('filters.transfers') },
  ];

  const directionOptions = [
    { value: null, label: t('filters.all') },
    { value: 'INBOUND' as Direction, label: t('filters.inbound') },
    { value: 'OUTBOUND' as Direction, label: t('filters.outbound') },
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

  const setState = (state: TxState | null) => {
    onChange({ ...filters, state: filters.state === state ? null : state });
  };

  const count = activeFilterCount(filters);
  const visibleStates: readonly TxState[] = statesExpanded
    ? TX_STATES
    : TX_STATES.slice(0, COLLAPSED_STATES);

  return (
    <Sheet visible={visible} onClose={onClose} title={t('filters.title')} snapRatio={0.86}>
      <ScrollView
        style={styles.scroll}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.content}
      >
        <Section title={t('filters.type')}>
          <Segmented
            options={typeOptions}
            value={filters.type}
            onChange={(value) => onChange({ ...filters, type: value })}
            accessibilityLabel={t('filters.typeA11y')}
            testID="filter-type"
          />
        </Section>

        <Section title={t('filters.direction')}>
          <Segmented
            options={directionOptions}
            value={filters.direction}
            onChange={(value) => onChange({ ...filters, direction: value })}
            accessibilityLabel={t('filters.directionA11y')}
            testID="filter-direction"
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

        <Section title={t('filters.state')}>
          <Text variant="bodySm" color="tertiary">
            {t('filters.stateHint')}
          </Text>
          <Spacer size={2} />
          {visibleStates.map((state) => (
            <View key={state}>
              <OptionRow
                label={stateLabel(state).label}
                description={stateLabel(state).description}
                selected={filters.state === state}
                onPress={() => setState(state)}
                testID={`filter-state-${state}`}
              />
              <Divider />
            </View>
          ))}
          <Spacer size={2} />
          <Pressable
            onPress={() => setStatesExpanded((value) => !value)}
            haptic="tap"
            scale="card"
            accessibilityLabel={
              statesExpanded ? t('filters.collapseStatesA11y') : t('filters.expandStatesA11y')
            }
            testID="filter-state-toggle"
          >
            <Text variant="labelMd" color="accent">
              {statesExpanded
                ? t('filters.collapseStates')
                : t('filters.showAllStates', { count: TX_STATES.length })}
            </Text>
          </Pressable>
        </Section>
      </ScrollView>

      <View style={[styles.footer, { borderTopColor: theme.overlay.hairline }]}>
        <Text variant="bodySm" color="secondary">
          {loading
            ? t('filters.searching')
            : resultCount === null
              ? '—'
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
