import { ScrollView, StyleSheet, View } from 'react-native';

import { Segmented } from '@/components/input';
import { Sheet } from '@/components/overlay';
import { Spacer, Text } from '@/components/primitives';
import { space } from '@/theme';
import { DriftScreen } from '@/devtools/drift/DriftScreen';
import { EnvironmentScreen } from '@/devtools/environment/EnvironmentScreen';
import { GalleryScreen } from '@/devtools/gallery/GalleryScreen';
import { JournalScreen } from '@/devtools/journal/JournalScreen';
import { NetworkInspector } from '@/devtools/network/NetworkInspector';
import { SessionInspector } from '@/devtools/session/SessionInspector';
import { SimulationScreen } from '@/devtools/simulation/SimulationScreen';
import { TransactionInspector } from '@/devtools/transactions/TransactionInspector';
import { DEVTOOLS_TABS, DEVTOOLS_TAB_LABELS, useDevtools } from './store';

const TAB_OPTIONS = DEVTOOLS_TABS.map((tab) => ({
  value: tab,
  label: DEVTOOLS_TAB_LABELS[tab],
}));

/**
 * Panneau de validation — `docs/10-validation-mode.md` §2.
 *
 * Feuille quasi plein écran, à onglets, **construite avec le design system** :
 * la validation ne dispense pas du soin. Le contenu n'est monté qu'une fois
 * l'onglet sélectionné — l'inspecteur de transactions lance une requête à son
 * montage, et un onglet fermé ne doit rien solliciter.
 */
export function DevtoolsPanel() {
  const open = useDevtools((state) => state.open);
  const tab = useDevtools((state) => state.tab);
  const setTab = useDevtools((state) => state.setTab);
  const close = useDevtools((state) => state.close);

  return (
    <Sheet visible={open} onClose={close} title="Mode validation" snapRatio={0.92}>
      {/* Huit onglets ne tiennent pas sur une seule ligne : le contrôle
          segmenté défile horizontalement plutôt que de comprimer ses libellés
          jusqu'à l'illisible. */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.tabs}
      >
        <Segmented
          options={TAB_OPTIONS}
          value={tab}
          onChange={(next) => setTab(next ?? 'network')}
          accessibilityLabel="Onglets du mode validation"
          testID="devtools-tabs"
        />
      </ScrollView>

      <Spacer size={4} />

      <View style={styles.body}>
        {tab === 'network' && <NetworkInspector />}
        {tab === 'transactions' && <TransactionInspector />}
        {tab === 'session' && <SessionInspector />}
        {tab === 'simulation' && <SimulationScreen />}
        {tab === 'environment' && <EnvironmentScreen />}
        {tab === 'drift' && <DriftScreen />}
        {tab === 'journal' && <JournalScreen />}
        {tab === 'gallery' && <GalleryScreen />}
      </View>

      <Text variant="labelSm" color="tertiary" align="center">
        Ce panneau n’existe pas en production.
      </Text>
    </Sheet>
  );
}

/** Chaque onglet doit rester lisible : pas de compression sous 96 dp. */
const TAB_MIN_WIDTH = 96;

const styles = StyleSheet.create({
  tabs: { minWidth: TAB_MIN_WIDTH * 8 },
  body: { flex: 1, marginBottom: space[2] },
});
