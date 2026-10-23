import { ScrollView, StyleSheet, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';

import { Button } from '@/components/action';
import { Divider, Spacer, Surface, Text } from '@/components/primitives';
import { radius, space, useTheme } from '@/theme';
import { CATEGORY_LABELS, type DriftFinding, type DriftSeverity } from './detector';
import { analyzeContract, API_DOCS_URL } from './useStartupDrift';

/**
 * Détecteur de dérive de contrat — `docs/10-validation-mode.md` §5.
 *
 * Exécuté à l'ouverture de l'onglet, et rejouable à la demande. Un écart
 * bloquant n'empêche pas l'app de fonctionner : il l'annonce, bruyamment, et
 * laisse continuer — le but est d'informer, pas de bloquer le travail.
 */
export function DriftScreen() {
  const theme = useTheme();

  const analysis = useQuery({
    queryKey: ['devtools', 'drift'] as const,
    queryFn: analyzeContract,
    staleTime: 0,
    gcTime: 0,
  });

  const report = analysis.data ?? null;

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      <View style={styles.toolbar}>
        <View style={styles.toolbarText}>
          <Text variant="labelMd">{API_DOCS_URL}</Text>
          <Text variant="bodySm" color="tertiary">
            {analysis.isFetching
              ? 'Analyse en cours…'
              : report
                ? `${report.blocking} bloquant · ${report.warning} avertissement · ${report.info} information`
                : 'Aucune analyse'}
          </Text>
        </View>
        <Button
          label="Analyser"
          variant="secondary"
          size="sm"
          fullWidth={false}
          loading={analysis.isFetching}
          onPress={() => void analysis.refetch()}
          testID="devtools-drift-run"
        />
      </View>

      <Spacer size={4} />

      {analysis.isError && (
        <Surface elevation={1} padding={4}>
          <Text variant="labelMd" color="danger">
            Document inaccessible
          </Text>
          <Spacer size={1} />
          <Text variant="bodySm" color="tertiary">
            {analysis.error instanceof Error ? analysis.error.message : 'Requête en échec'}
          </Text>
        </Surface>
      )}

      {report?.clean && (
        <Surface elevation={1} padding={4}>
          <Text variant="labelMd" color="success">
            Aucun écart
          </Text>
          <Spacer size={1} />
          <Text variant="bodySm" color="tertiary">
            L’application suit exactement le contrat servi par le backend.
          </Text>
        </Surface>
      )}

      {report?.findings.map((finding, index) => (
        <View key={`${finding.category}-${finding.subject}-${index}`} style={styles.card}>
          <Surface elevation={1} padding={4}>
            <View style={styles.row}>
              <View
                style={[
                  styles.dot,
                  { backgroundColor: severityColor(finding.severity, theme), borderRadius: radius.full },
                ]}
              />
              <Text variant="labelSm" color="secondary">
                {CATEGORY_LABELS[finding.category].toUpperCase()}
              </Text>
            </View>
            <Spacer size={2} />
            <Text variant="monoMd" numberOfLines={1}>
              {finding.subject}
            </Text>
            <Spacer size={1} />
            <Text variant="bodySm" color="tertiary">
              {finding.message}
            </Text>
          </Surface>
        </View>
      ))}

      {report && !report.clean && (
        <>
          <Spacer size={4} />
          <Divider />
          <Spacer size={3} />
          <Text variant="bodySm" color="tertiary">
            Les écarts bloquants exigent une mise à jour de `docs/01-api-contract.md` **avant**
            toute modification de code — `docs/09-api-evolution.md` §2.
          </Text>
        </>
      )}
    </ScrollView>
  );
}

function severityColor(severity: DriftSeverity, theme: ReturnType<typeof useTheme>): string {
  if (severity === 'blocking') return theme.status.failed.fg;
  if (severity === 'warning') return theme.status.pending.fg;
  return theme.text.tertiary;
}

/** Réexporté pour le panneau, qui affiche un compteur dans l'onglet. */
export type { DriftFinding };

const DOT_SIZE = 8;

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingBottom: space[8] },
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space[3],
  },
  toolbarText: { flexShrink: 1, gap: space[1] },
  card: { marginBottom: space[3] },
  row: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
  dot: { width: DOT_SIZE, height: DOT_SIZE },
});
