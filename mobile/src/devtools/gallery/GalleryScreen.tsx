import { ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button, IconButton } from '@/components/action';
import { StateTimeline, StatusChip, TransactionRow } from '@/components/display';
import { Skeleton } from '@/components/feedback';
import { Amount } from '@/components/money';
import { Divider, Icon, ICON_NAMES, Pressable, Spacer, Surface, Text } from '@/components/primitives';
import { TX_STATES, type StateTransition, type Transaction } from '@/types/domain';
import {
  iconSize,
  palette,
  radius,
  space,
  stroke,
  type as typeScale,
  useTheme,
  useThemeContext,
  type RadiusToken,
  type SpaceToken,
  type TypeToken,
} from '@/theme';

/** Jeux d'essai de la galerie — jamais de données réseau ici. */
const GALLERY_TRANSACTIONS: Transaction[] = [
  {
    id: 'g1',
    reference: 'TRX-20260806-A3F91C2D',
    type: 'CASH_IN',
    direction: 'INBOUND',
    state: 'COMPLETED',
    outcome: 'success',
    amount: { minor: 50000, currency: 'XOF' },
    paymentMethod: 'ORANGE_MONEY',
    counterpart: null,
    createdAt: new Date(),
    stateHistory: null,
  },
  {
    id: 'g2',
    reference: 'TRX-20260806-B1C2D3E4',
    type: 'P2P_TRANSFER',
    direction: 'OUTBOUND',
    state: 'CAPTURED',
    outcome: 'pending',
    amount: { minor: 25000, currency: 'XOF' },
    paymentMethod: 'WALLET',
    counterpart: '+225070***011',
    createdAt: new Date(),
    stateHistory: null,
  },
  {
    id: 'g3',
    reference: 'TRX-20260806-C9D8E7F6',
    type: 'CASH_OUT',
    direction: 'OUTBOUND',
    state: 'AUTHORIZATION_FAILED',
    outcome: 'failed',
    amount: { minor: 10000, currency: 'XOF' },
    paymentMethod: 'WAVE',
    counterpart: null,
    createdAt: new Date(),
    stateHistory: null,
  },
];

const GALLERY_TIMELINE: StateTransition[] = [
  { from: null, to: 'INITIALIZED', occurredAt: new Date(Date.now() - 3000) },
  { from: 'INITIALIZED', to: 'AUTHORIZED', occurredAt: new Date(Date.now() - 2000) },
  { from: 'AUTHORIZED', to: 'CAPTURED', occurredAt: new Date(Date.now() - 1000) },
];

/**
 * Galerie du design system — exigée par le lot 2 de `07-implementation-plan.md`.
 *
 * Elle n'est pas un gadget : c'est l'unique moyen de vérifier qu'un changement
 * de token n'a rien cassé ailleurs, et que le thème clair ne rompt aucun
 * contraste. Elle vit sous `src/devtools/` et n'existe qu'en développement.
 */
export function GalleryScreen() {
  const theme = useTheme();
  const { scheme, preference, setPreference, reduceMotion } = useThemeContext();
  const insets = useSafeAreaInsets();

  return (
    <ScrollView
      style={{ backgroundColor: theme.bg.app }}
      contentContainerStyle={{
        padding: space[5],
        paddingTop: insets.top + space[5],
        paddingBottom: insets.bottom + space[16],
      }}
    >
      <Text variant="titleLg">Design system</Text>
      <Text variant="bodySm" color="tertiary">
        thème {scheme} · préférence {preference} · animations réduites{' '}
        {reduceMotion ? 'oui' : 'non'}
      </Text>

      <Spacer size={4} />
      <View style={styles.row}>
        {(['system', 'dark', 'light'] as const).map((option) => (
          <Pressable
            key={option}
            onPress={() => setPreference(option)}
            haptic="select"
            scale="card"
            accessibilityLabel={`Thème ${option}`}
          >
            <Surface
              elevation={preference === option ? 3 : 1}
              radius="sm"
              padding={3}
              bordered={preference === option}
              style={styles.chip}
            >
              <Text variant="labelMd" color={preference === option ? 'primary' : 'tertiary'}>
                {option}
              </Text>
            </Surface>
          </Pressable>
        ))}
      </View>

      <Section title="Neutres" subtitle="11 paliers — §2.1">
        <Swatches
          entries={Object.entries(palette.neutral).map(([key, value]) => [`neutral.${key}`, value])}
        />
      </Section>

      <Section title="Accent" subtitle="Kora Green, 6 paliers — §2.2">
        <Swatches
          entries={Object.entries(palette.accent).map(([key, value]) => [`accent.${key}`, value])}
        />
      </Section>

      <Section title="Sémantique" subtitle="4 familles d'état — §2.3, §2.4">
        <Swatches
          entries={[
            ['danger.500', palette.danger[500]],
            ['warning.500', palette.warning[500]],
            ['info.500', palette.info[500]],
            ['pending.500', palette.pending[500]],
          ]}
        />
      </Section>

      <Section title="États de transaction" subtitle="table faisant autorité — §2.4">
        {(['success', 'pending', 'failed', 'reversed'] as const).map((family) => (
          <View key={family} style={styles.statusRow}>
            <View style={[styles.statusChip, { backgroundColor: theme.status[family].bg }]}>
              <Text variant="labelSm" tint={theme.status[family].fg}>
                {family}
              </Text>
            </View>
            <Text variant="bodySm" color="tertiary">
              status.{family}
            </Text>
          </View>
        ))}
      </Section>

      <Section title="Flux monétaire" subtitle="une sortie ne se colore JAMAIS en rouge — §2.5">
        <View style={styles.statusRow}>
          <Text variant="titleMd" tabular tint={theme.flow.inbound}>
            + 50 000 F
          </Text>
          <Text variant="titleMd" tabular tint={theme.flow.outbound}>
            − 25 000 F
          </Text>
        </View>
      </Section>

      <Section title="Typographie" subtitle="12 variantes — §3.2">
        {(Object.keys(typeScale) as TypeToken[]).map((token) => (
          <View key={token} style={styles.typeRow}>
            <Text variant="labelSm" color="tertiary">
              {token} · {typeScale[token].fontSize}/{typeScale[token].lineHeight}
            </Text>
            <Text variant={token} numberOfLines={1}>
              125 000 F — Aminata
            </Text>
          </View>
        ))}
      </Section>

      <Section title="Chiffres tabulaires" subtitle="aucun décalage horizontal — §3.4">
        <Text variant="displayMd" tabular>
          111 111 F
        </Text>
        <Text variant="displayMd" tabular>
          999 999 F
        </Text>
      </Section>

      <Section title="Espacement" subtitle="base 4, onze crans — §4">
        {(Object.keys(space) as unknown as SpaceToken[]).map((token) => (
          <View key={String(token)} style={styles.scaleRow}>
            <Text variant="labelSm" color="tertiary" style={styles.scaleLabel}>
              space.{String(token)} · {space[token]}
            </Text>
            <View
              style={{
                height: space[4],
                width: space[token],
                backgroundColor: theme.accent.primary,
                borderRadius: radius.xs,
              }}
            />
          </View>
        ))}
      </Section>

      <Section title="Rayons" subtitle="proportionnels à la taille — §5.1">
        <View style={styles.row}>
          {(Object.keys(radius) as RadiusToken[])
            .filter((token) => token !== 'full')
            .map((token) => (
              <View key={token} style={styles.radiusCell}>
                <View
                  style={{
                    width: 56,
                    height: 56,
                    backgroundColor: theme.bg.surface3,
                    borderRadius: radius[token],
                  }}
                />
                <Text variant="labelSm" color="tertiary">
                  {token}
                </Text>
              </View>
            ))}
        </View>
      </Section>

      <Section title="Élévation" subtitle="luminosité de surface, pas ombre — §5.3">
        {([0, 1, 2, 3, 4] as const).map((level) => (
          <View key={level} style={styles.elevationCell}>
            <Surface elevation={level} radius="md" padding={4}>
              <Text variant="bodyMd">elevation {level}</Text>
              <Text variant="bodySm" color="tertiary">
                {level === 4 ? 'flottant, ombre + bordure' : 'surface pleine'}
              </Text>
            </Surface>
          </View>
        ))}
      </Section>

      <Section title="Filets" subtitle="un pixel physique — §5.2">
        <Text variant="bodySm" color="tertiary">
          hairline = {stroke.hairline.toFixed(3)} dp
        </Text>
        <Spacer size={2} />
        <Divider />
        <Spacer size={2} />
        <Divider strong />
      </Section>

      <Section title={`Icônes (${ICON_NAMES.length})`} subtitle="SVG, trait 1,5 dp — §6">
        <View style={styles.iconGrid}>
          {ICON_NAMES.map((name) => (
            <View key={name} style={styles.iconCell}>
              <View
                style={[
                  styles.iconBadge,
                  { backgroundColor: theme.bg.surface2, borderRadius: radius.full },
                ]}
              >
                <Icon name={name} size="md" color={theme.text.secondary} />
              </View>
              <Text variant="labelSm" color="tertiary" align="center" numberOfLines={1}>
                {name}
              </Text>
            </View>
          ))}
        </View>
      </Section>

      <Section title="Tailles d'icône" subtitle="5 valeurs autorisées — §6.1">
        <View style={styles.row}>
          {(Object.keys(iconSize) as (keyof typeof iconSize)[]).map((token) => (
            <View key={token} style={styles.radiusCell}>
              <Icon name="check-circle" size={token} color={theme.accent.primary} />
              <Text variant="labelSm" color="tertiary">
                {token} · {iconSize[token]}
              </Text>
            </View>
          ))}
        </View>
      </Section>

      <Section title="Boutons" subtitle="4 variantes, 3 tailles — §6">
        <View style={styles.buttonStack}>
          {(['primary', 'secondary', 'ghost', 'danger'] as const).map((variant) => (
            <Button key={variant} label={variant} onPress={() => undefined} variant={variant} />
          ))}
          <Button label="Chargement — la largeur ne bouge pas" onPress={() => undefined} loading />
          <Button label="Désactivé" onPress={() => undefined} disabled />
          <View style={styles.row}>
            <Button label="md" onPress={() => undefined} size="md" fullWidth={false} />
            <Button label="sm" onPress={() => undefined} size="sm" fullWidth={false} />
          </View>
        </View>
      </Section>

      <Section title="Boutons d'icône" subtitle="compression 0,88 — §4">
        <View style={styles.row}>
          <IconButton name="eye" onPress={() => undefined} accessibilityLabel="Afficher" surface />
          <IconButton name="copy" onPress={() => undefined} accessibilityLabel="Copier" surface />
          <IconButton name="filter" onPress={() => undefined} accessibilityLabel="Filtrer" />
          <IconButton
            name="close"
            onPress={() => undefined}
            accessibilityLabel="Fermer"
            disabled
          />
        </View>
      </Section>

      <Section title="Squelettes" subtitle="jamais d'indicateur circulaire — §6.5">
        <View style={styles.skeletonStack}>
          <Skeleton height={44} radius="full" width={44} />
          <Skeleton height={20} width="60%" />
          <Skeleton height={14} width="40%" />
          <Skeleton height={68} radius="lg" />
        </View>
      </Section>

      <Section title="Montants" subtitle="trois blocs, symbole à 0,45× — §3.4">
        <View style={styles.buttonStack}>
          <Amount minor={125000} currency="XOF" size="displayXl" />
          <Amount minor={50000} currency="XOF" size="titleLg" sign="always" direction="INBOUND" />
          <Amount minor={25000} currency="XOF" size="titleLg" direction="OUTBOUND" />
          <Amount minor={125000} currency="XOF" size="titleMd" hidden />
          <Amount minor={123456} currency="EUR" size="titleMd" />
        </View>
      </Section>

      <Section title="Puces d'état" subtitle="les 11 états, 4 familles — §2.4">
        <View style={styles.row}>
          {TX_STATES.map((state) => (
            <StatusChip key={state} state={state} detailed />
          ))}
        </View>
      </Section>

      <Section title="Lignes d'historique" subtitle="succès sans puce — §5">
        {GALLERY_TRANSACTIONS.map((transaction) => (
          <TransactionRow key={transaction.id} transaction={transaction} />
        ))}
      </Section>

      <Section title="Frise des états" subtitle="le composant signature — §6.7">
        <StateTimeline history={GALLERY_TIMELINE} currentState="CAPTURED" />
      </Section>

      <Section title="Rôles de texte" subtitle="§3.3">
        {(['primary', 'secondary', 'tertiary', 'disabled', 'danger', 'success', 'accent'] as const).map(
          (role) => (
            <Text key={role} variant="bodyMd" color={role}>
              text.{role} — 125 000 F
            </Text>
          ),
        )}
        <Spacer size={2} />
        <View style={[styles.onAccent, { backgroundColor: theme.accent.primary }]}>
          <Text variant="labelMd" color="onAccent">
            text.onAccent sur accent.primary
          </Text>
        </View>
      </Section>
    </ScrollView>
  );
}

function Section({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
}) {
  return (
    <View style={styles.section}>
      <Text variant="titleSm">{title}</Text>
      <Text variant="bodySm" color="tertiary">
        {subtitle}
      </Text>
      <Spacer size={3} />
      {children}
    </View>
  );
}

function Swatches({ entries }: { entries: [string, string][] }) {
  const theme = useTheme();
  return (
    <View style={styles.row}>
      {entries.map(([label, value]) => (
        <View key={label} style={styles.swatchCell}>
          <View
            style={{
              width: 52,
              height: 52,
              backgroundColor: value,
              borderRadius: radius.sm,
              borderWidth: stroke.hairline,
              borderColor: theme.overlay.border,
            }}
          />
          <Text variant="labelSm" color="tertiary" align="center">
            {label.split('.')[1]}
          </Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  section: { marginTop: space[8] },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: space[3] },
  chip: { alignItems: 'center' },
  swatchCell: { alignItems: 'center', gap: space[1] },
  radiusCell: { alignItems: 'center', gap: space[1] },
  elevationCell: { marginBottom: space[3] },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: space[3], marginBottom: space[2] },
  statusChip: { paddingHorizontal: space[3], paddingVertical: space[1], borderRadius: radius.xs },
  typeRow: { marginBottom: space[4], gap: space[1] },
  scaleRow: { flexDirection: 'row', alignItems: 'center', gap: space[3], marginBottom: space[2] },
  scaleLabel: { width: 110 },
  iconGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: space[3] },
  iconCell: { width: 72, alignItems: 'center', gap: space[1] },
  iconBadge: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  onAccent: { padding: space[4], borderRadius: radius.md, alignItems: 'center' },
  buttonStack: { gap: space[3] },
  skeletonStack: { gap: space[3] },
});

export default GalleryScreen;
