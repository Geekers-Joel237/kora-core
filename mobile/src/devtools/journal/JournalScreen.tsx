import { useCallback, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import * as Clipboard from 'expo-clipboard';

import { Button, IconButton } from '@/components/action';
import { useToast } from '@/components/feedback';
import { Segmented, TextField } from '@/components/input';
import { Divider, Spacer, Surface, Text } from '@/components/primitives';
import { space, useTheme } from '@/theme';
import { STATUS_LABELS, toMarkdown, useJournal, type JournalStatus } from './store';

const STATUS_OPTIONS: { value: JournalStatus; label: string }[] = [
  { value: 'expected', label: 'Conforme' },
  { value: 'unexpected', label: 'Écart' },
  { value: 'open', label: 'À confirmer' },
];

const EMPTY_FORM = {
  title: '',
  scenario: '',
  correlationId: '',
  observed: '',
  conclusion: '',
  status: 'open' as JournalStatus,
};

/**
 * Journal de scénarios — `docs/10-validation-mode.md` §9.
 *
 * L'export Markdown alimente directement le journal de compatibilité de
 * `docs/09-api-evolution.md` §7. C'est le seul artefact du mode validation
 * destiné à sortir de l'appareil, et il part par le presse-papiers : rien
 * n'est envoyé nulle part depuis les devtools.
 */
export function JournalScreen() {
  const theme = useTheme();
  const toast = useToast();
  const entries = useJournal((state) => state.entries);
  const add = useJournal((state) => state.add);
  const remove = useJournal((state) => state.remove);
  const clear = useJournal((state) => state.clear);

  const [form, setForm] = useState(EMPTY_FORM);

  const submit = useCallback(() => {
    if (form.title.trim().length === 0) return;
    const scenario = Number.parseInt(form.scenario, 10);
    add({
      title: form.title.trim(),
      scenario: Number.isNaN(scenario) ? null : scenario,
      correlationId: form.correlationId.trim() || null,
      observed: form.observed.trim(),
      conclusion: form.conclusion.trim(),
      status: form.status,
    });
    setForm(EMPTY_FORM);
    toast.show({ message: 'Observation consignée', icon: 'check-circle', tone: 'success' });
  }, [add, form, toast]);

  const exportMarkdown = useCallback(async () => {
    await Clipboard.setStringAsync(toMarkdown(entries));
    toast.show({ message: 'Journal copié en Markdown', icon: 'copy', tone: 'success' });
  }, [entries, toast]);

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
      keyboardShouldPersistTaps="handled"
    >
      <Surface elevation={1} padding={4}>
        <Text variant="labelMd">Nouvelle observation</Text>
        <Spacer size={3} />
        <TextField
          label="Titre"
          value={form.title}
          onChangeText={(title) => setForm((state) => ({ ...state, title }))}
          placeholder="Transfert 25 000 XOF, réseau coupé à 800 ms"
          autoCapitalize="sentences"
          testID="journal-title"
        />
        <Spacer size={3} />
        <TextField
          label="Scénario §11 (optionnel)"
          value={form.scenario}
          onChangeText={(scenario) => setForm((state) => ({ ...state, scenario }))}
          placeholder="2"
          keyboardType="number-pad"
          testID="journal-scenario"
        />
        <Spacer size={3} />
        <TextField
          label="Corrélation (optionnel)"
          value={form.correlationId}
          onChangeText={(correlationId) => setForm((state) => ({ ...state, correlationId }))}
          placeholder="a3f2-…"
          testID="journal-correlation"
        />
        <Spacer size={3} />
        <TextField
          label="Observé"
          value={form.observed}
          onChangeText={(observed) => setForm((state) => ({ ...state, observed }))}
          placeholder="Aucune réponse client, transaction COMPLETED 4 s plus tard"
          autoCapitalize="sentences"
          testID="journal-observed"
        />
        <Spacer size={3} />
        <TextField
          label="Conclusion"
          value={form.conclusion}
          onChangeText={(conclusion) => setForm((state) => ({ ...state, conclusion }))}
          placeholder="L’écran « issue incertaine » est le bon comportement"
          autoCapitalize="sentences"
          testID="journal-conclusion"
        />
        <Spacer size={4} />
        <Segmented
          options={STATUS_OPTIONS}
          value={form.status}
          onChange={(status) => setForm((state) => ({ ...state, status: status ?? 'open' }))}
          accessibilityLabel="Statut de l’observation"
          testID="journal-status"
        />
        <Spacer size={4} />
        <Button
          label="Consigner"
          onPress={submit}
          disabled={form.title.trim().length === 0}
          testID="journal-submit"
        />
      </Surface>

      <Spacer size={6} />

      <View style={styles.toolbar}>
        <Text variant="labelMd">
          {entries.length} observation{entries.length > 1 ? 's' : ''}
        </Text>
        <View style={styles.toolbarActions}>
          {entries.length > 0 && (
            <Button
              label="Vider"
              variant="ghost"
              size="sm"
              fullWidth={false}
              onPress={clear}
              testID="journal-clear"
            />
          )}
          <Button
            label="Exporter"
            variant="secondary"
            size="sm"
            icon="copy"
            fullWidth={false}
            disabled={entries.length === 0}
            onPress={() => void exportMarkdown()}
            testID="journal-export"
          />
        </View>
      </View>

      <Spacer size={3} />

      {entries.map((entry) => (
        <View key={entry.id} style={styles.card}>
          <Surface elevation={1} padding={4}>
            <View style={styles.row}>
              <Text variant="labelMd" style={styles.grow} numberOfLines={2}>
                {entry.title}
              </Text>
              <IconButton
                name="close"
                onPress={() => remove(entry.id)}
                accessibilityLabel="Supprimer l’observation"
                testID={`journal-remove-${entry.id}`}
              />
            </View>
            <Text variant="bodySm" color="tertiary">
              {new Date(entry.at).toLocaleString('fr-FR')}
              {entry.scenario === null ? '' : ` · §11 #${entry.scenario}`}
            </Text>
            {entry.correlationId && (
              <Text variant="monoMd" color="tertiary" numberOfLines={1}>
                {entry.correlationId}
              </Text>
            )}
            {(entry.observed.length > 0 || entry.conclusion.length > 0) && (
              <>
                <Spacer size={2} />
                <Divider />
                <Spacer size={2} />
              </>
            )}
            {entry.observed.length > 0 && (
              <Text variant="bodySm" color="secondary">
                Observé — {entry.observed}
              </Text>
            )}
            {entry.conclusion.length > 0 && (
              <Text variant="bodySm" color="secondary">
                Conclusion — {entry.conclusion}
              </Text>
            )}
            <Spacer size={2} />
            <Text
              variant="labelSm"
              tint={
                entry.status === 'expected'
                  ? theme.status.success.fg
                  : entry.status === 'unexpected'
                    ? theme.status.failed.fg
                    : theme.text.tertiary
              }
            >
              {STATUS_LABELS[entry.status]}
            </Text>
          </Surface>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingBottom: space[8] },
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space[3],
  },
  toolbarActions: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
  card: { marginBottom: space[3] },
  row: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
  grow: { flexShrink: 1, flexGrow: 1 },
});
