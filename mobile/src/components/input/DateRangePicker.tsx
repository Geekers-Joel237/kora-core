import { useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { IconButton } from '@/components/action';
import { Icon, Pressable, Text } from '@/components/primitives';
import { dayKey, formatRangeBound } from '@/lib/datetime';
import { layout, radius, space, useTheme } from '@/theme';

export interface DateRangePickerProps {
  from: Date | null;
  to: Date | null;
  onChange: (from: Date | null, to: Date | null) => void;
  /** Aucune opération n'existe dans le futur : les jours au-delà sont inertes. */
  maxDate?: Date;
}

const WEEKDAYS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];
const MONTHS = [
  'janvier',
  'février',
  'mars',
  'avril',
  'mai',
  'juin',
  'juillet',
  'août',
  'septembre',
  'octobre',
  'novembre',
  'décembre',
];
const CELL_SIZE = 40;
const WEEK_LENGTH = 7;

/**
 * Sélecteur de plage — grille mensuelle écrite à la main.
 *
 * Aucun sélecteur natif n'est utilisé : `@react-native-community/datetimepicker`
 * ouvre un composant système impossible à faire tenir dans le langage visuel du
 * §2 du design system, et impose une reconstruction native. Une grille de sept
 * colonnes tient en un composant, se thème comme le reste, et se teste.
 *
 * Deux appuis suffisent : le premier fixe le début, le second la fin. Un appui
 * antérieur au début en cours redevient un début — c'est le geste attendu quand
 * on s'aperçoit qu'on a commencé par la mauvaise borne.
 */
export function DateRangePicker({ from, to, onChange, maxDate }: DateRangePickerProps) {
  const theme = useTheme();
  const ceiling = maxDate ?? new Date();

  const [cursor, setCursor] = useState<Date>(() => {
    const anchor = from ?? ceiling;
    return new Date(anchor.getFullYear(), anchor.getMonth(), 1);
  });

  const cells = useMemo(() => buildMonthGrid(cursor), [cursor]);

  const fromKey = from ? dayKey(from) : null;
  const toKey = to ? dayKey(to) : null;

  const select = (day: Date) => {
    if (!from || (from && to)) {
      onChange(day, null);
      return;
    }
    if (day.getTime() < from.getTime()) {
      onChange(day, null);
      return;
    }
    onChange(from, day);
  };

  const shiftMonth = (delta: number) =>
    setCursor((current) => new Date(current.getFullYear(), current.getMonth() + delta, 1));

  const monthLabel = `${MONTHS[cursor.getMonth()] ?? ''} ${cursor.getFullYear()}`;
  const nextDisabled =
    cursor.getFullYear() > ceiling.getFullYear() ||
    (cursor.getFullYear() === ceiling.getFullYear() && cursor.getMonth() >= ceiling.getMonth());

  return (
    <View>
      <View style={styles.header}>
        <IconButton
          name="chevron-left"
          onPress={() => shiftMonth(-1)}
          accessibilityLabel="Mois précédent"
          testID="calendar-prev"
        />
        <Text variant="labelMd">{monthLabel}</Text>
        <IconButton
          name="chevron-right"
          onPress={() => shiftMonth(1)}
          disabled={nextDisabled}
          accessibilityLabel="Mois suivant"
          testID="calendar-next"
        />
      </View>

      <View style={styles.week}>
        {WEEKDAYS.map((weekday, index) => (
          <View key={`${weekday}-${index}`} style={styles.cell}>
            <Text variant="labelSm" color="tertiary" align="center">
              {weekday}
            </Text>
          </View>
        ))}
      </View>

      {chunk(cells, WEEK_LENGTH).map((row, rowIndex) => (
        <View key={rowIndex} style={styles.week}>
          {row.map((day, dayIndex) => {
            if (!day) return <View key={`empty-${dayIndex}`} style={styles.cell} />;

            const key = dayKey(day);
            const isStart = key === fromKey;
            const isEnd = key === toKey;
            const inRange =
              from !== null &&
              to !== null &&
              day.getTime() > from.getTime() &&
              day.getTime() < to.getTime();
            const disabled = day.getTime() > ceiling.getTime();

            return (
              <Pressable
                key={key}
                onPress={() => select(day)}
                disabled={disabled}
                haptic="select"
                scale="card"
                accessibilityLabel={formatRangeBound(day, ceiling)}
                accessibilityState={{ selected: isStart || isEnd }}
                testID={`day-${key}`}
                style={[styles.cell, inRange && { backgroundColor: theme.accent.wash }]}
              >
                <View
                  style={[
                    styles.dayBubble,
                    { borderRadius: radius.full },
                    (isStart || isEnd) && { backgroundColor: theme.accent.primary },
                  ]}
                >
                  <Text
                    variant="bodySm"
                    color={disabled ? 'disabled' : isStart || isEnd ? 'onAccent' : 'primary'}
                    align="center"
                    tabular
                  >
                    {day.getDate()}
                  </Text>
                </View>
              </Pressable>
            );
          })}
        </View>
      ))}

      <View style={styles.summary}>
        <Icon name="calendar" size="xs" color={theme.text.tertiary} />
        <Text variant="bodySm" color="tertiary">
          {from === null
            ? 'Choisissez une date de début'
            : to === null
              ? `Depuis le ${formatRangeBound(from, ceiling)} — choisissez la fin`
              : `${formatRangeBound(from, ceiling)} → ${formatRangeBound(to, ceiling)}`}
        </Text>
      </View>
    </View>
  );
}

/**
 * Grille alignée sur le lundi. `getDay()` renvoie 0 pour dimanche : sans ce
 * décalage, tout le mois se décale d'un jour en France comme en Côte d'Ivoire.
 */
function buildMonthGrid(cursor: Date): (Date | null)[] {
  const year = cursor.getFullYear();
  const month = cursor.getMonth();
  const first = new Date(year, month, 1);
  const leading = (first.getDay() + 6) % WEEK_LENGTH;
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  const cells: (Date | null)[] = Array.from({ length: leading }, () => null);
  for (let day = 1; day <= daysInMonth; day += 1) cells.push(new Date(year, month, day));
  while (cells.length % WEEK_LENGTH !== 0) cells.push(null);

  return cells;
}

function chunk<T>(items: T[], size: number): T[][] {
  const rows: T[][] = [];
  for (let index = 0; index < items.length; index += size) {
    rows.push(items.slice(index, index + size));
  }
  return rows;
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    height: layout.minTouchTarget,
  },
  week: { flexDirection: 'row' },
  cell: {
    flex: 1,
    height: CELL_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dayBubble: {
    width: CELL_SIZE - space[2],
    height: CELL_SIZE - space[2],
    alignItems: 'center',
    justifyContent: 'center',
  },
  summary: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[2],
    paddingTop: space[3],
  },
});