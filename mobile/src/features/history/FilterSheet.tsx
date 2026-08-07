import { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

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
  PERIOD_LABELS,
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

const TYPE_OPTIONS = [
  { value: null, label: 'Tous' },
  { value: 'CASH_IN' as TxType, label: 'Dépôts' },
  { value: 'CASH_OUT' as TxType, label: 'Retraits' },
  { value: 'P2P_TRANSFER' as TxType, label: 'Transferts' },
];

const DIRECTION_OPTIONS = [
  { value: null, label: 'Tous' },
  { value: 'INBOUND' as Direction, label: 'Entrants' },
  { value: 'OUTBOUND' as Direction, label: 'Sortants' },
];

const PERIOD_OPTIONS: { value: PeriodPreset | 'custom' | null; label: string }[] = [
  { value: null, label: 'Tout' },
  ...PERIOD_PRESETS.map((preset) => ({ value: preset, label: PERIOD_LABELS[preset] })),
  { value: 'custom', label: 'Perso.' },
];

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
  const theme = useTheme();
  const [statesExpanded, setStatesExpanded] = useState(false);

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
  const visibleStates: readonly TxState[] = statesExpanded ? TX_STATES : TX_STATES.slice(0, 4);

  return (
    <Sheet visible={visible} onClose={onClose} title="Filtrer" snapRatio={0.86}>
      <ScrollView
        style={styles.scroll}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.content}
      >
        <Section title="Type">
          <Segmented
            options={TYPE_OPTIONS}
            value={filters.type}
            onChange={(value) => onChange({ ...filters, type: value })}
            accessibilityLabel="Type d’opération"
            testID="filter-type"
          />
        </Section>

        <Section title="Sens">
          <Segmented
            options={DIRECTION_OPTIONS}
            value={filters.direction}
            onChange={(value) => onChange({ ...filters, direction: value })}
            accessibilityLabel="Sens de l’opération"
            testID="filter-direction"
          />
        </Section>

        <Section title="Période">
          <Segmented
            options={PERIOD_OPTIONS}
            value={periodValue}
            onChange={setPeriod}
            accessibilityLabel="Période"
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

        <Section title="État">
          <Text variant="bodySm" color="tertiary">
            Un seul état à la fois — le serveur n’en accepte pas davantage.
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
            accessibilityLabel={statesExpanded ? 'Réduire la liste des états' : 'Voir tous les états'}
            testID="filter-state-toggle"
          >
            <Text variant="labelMd" color="accent">
              {statesExpanded ? 'Réduire' : `Voir les ${TX_STATES.length} états`}
            </Text>
          </Pressable>
        </Section>
      </ScrollView>

      <View style={[styles.footer, { borderTopColor: theme.overlay.hairline }]}>
        <Text variant="bodySm" color="secondary">
          {loading
            ? 'Recherche…'
            : resultCount === null
              ? '—'
              : `${resultCount} opération${resultCount > 1 ? 's' : ''}`}
        </Text>
        <View style={styles.footerActions}>
          {count > 0 && (
            <Button
              label="Réinitialiser"
              variant="ghost"
              size="sm"
              fullWidth={false}
              onPress={() => onChange(NO_FILTERS)}
              testID="filter-reset"
            />
          )}
          <Button
            label="Voir"
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