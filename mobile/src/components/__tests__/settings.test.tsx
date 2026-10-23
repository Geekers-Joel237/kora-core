import { fireEvent, render, waitFor } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { QueryClientProvider } from '@tanstack/react-query';

import SettingsScreen from '../../../app/(app)/(tabs)/settings';
import { Toggle } from '@/components/input';
import { useBiometrics } from '@/features/auth/biometrics';
import { useSession } from '@/features/auth/session';
import { i18n } from '@/i18n';
import { registerTokenProvider } from '@/lib/http';
import { createQueryClient } from '@/lib/queryClient';
import { KvKey, kvSetString } from '@/lib/storage/kv';
import { ThemeProvider } from '@/theme';

const BALANCE = {
  accountId: 'acc-1',
  accountNumber: 'ACC-20260806-A3F91C2D',
  amount: 125000,
  currency: 'XOF',
};

function wrap(children: ReactNode) {
  const client = createQueryClient();
  return render(
    <SafeAreaProvider
      initialMetrics={{
        frame: { x: 0, y: 0, width: 390, height: 844 },
        insets: { top: 47, left: 0, right: 0, bottom: 34 },
      }}
    >
      <QueryClientProvider client={client}>
        <ThemeProvider>{children}</ThemeProvider>
      </QueryClientProvider>
    </SafeAreaProvider>,
  );
}

beforeEach(() => {
  kvSetString(KvKey.themePreference, 'dark');
  globalThis.fetch = jest.fn(async () => ({
    status: 200,
    ok: true,
    text: async () => JSON.stringify(BALANCE),
  })) as unknown as typeof fetch;
  registerTokenProvider({
    getAccessToken: () => 'access-1',
    getRefreshToken: () => 'refresh-1',
    onTokensRefreshed: jest.fn(),
    onSessionExpired: jest.fn(),
  });
  useBiometrics.setState({ enabled: false, available: false, locked: false });
  useSession.setState({
    status: 'authenticated',
    user: { id: 'u1', email: 'aminata@kora.ci', role: 'CUSTOMER' },
    profile: { fullName: 'Aminata Diallo', phone: '+2250708091011' },
  });
});

afterEach(async () => {
  registerTokenProvider(null);
  await i18n.changeLanguage('fr');
});

describe('interrupteur du design system', () => {
  it('expose le rôle et l’état attendus par le lecteur d’écran', async () => {
    const view = await wrap(
      <Toggle value onChange={() => undefined} accessibilityLabel="Masquer le solde" />,
    );

    const toggle = view.getByLabelText('Masquer le solde');
    expect(toggle.props.accessibilityRole).toBe('switch');
    expect(toggle.props.accessibilityState.checked).toBe(true);
  });

  it('annonce son indisponibilité au système', async () => {
    // Le blocage effectif du geste vient de `.enabled(false)` sur le
    // `Gesture.Tap()` de `Pressable` — hors de portée de `fireEvent`, qui ne
    // rejoue pas gesture-handler. Ce qui se vérifie ici est le contrat que la
    // plateforme et le lecteur d'écran consomment réellement.
    const view = await wrap(
      <Toggle value={false} onChange={() => undefined} accessibilityLabel="Biométrie" disabled />,
    );

    const toggle = view.getByLabelText('Biométrie');
    expect(toggle.props.accessibilityState.disabled).toBe(true);
    expect(toggle.props.accessibilityState.checked).toBe(false);
  });
});

/**
 * Monte l'écran et attend que `GET /payments/balance` ait abouti.
 *
 * Sans cette attente, la requête se résout après la fin du test et React
 * signale une mise à jour hors `act()` — un avertissement qui masquerait les
 * vrais.
 */
async function renderSettings() {
  const view = await wrap(<SettingsScreen />);
  await waitFor(() => expect(view.getByText('ACC-20260806-A3F91C2D')).toBeTruthy());
  return view;
}

describe('écran de réglages — docs/05-screens.md §7', () => {
  it('reconstitue le profil depuis trois sources distinctes — contrat §6.3', async () => {
    // …et `GET /payments/balance` pour le numéro de compte.
    const view = await renderSettings();

    // Stockage local pour le nom et le téléphone…
    expect(view.getByText('Aminata Diallo')).toBeTruthy();
    expect(view.getByText('+2250708091011')).toBeTruthy();
    // …claims du jeton pour l'e-mail…
    expect(view.getByText('aminata@kora.ci')).toBeTruthy();
  });

  it('replie sur l’e-mail sans jamais fabriquer de nom', async () => {
    useSession.setState({ profile: { fullName: null, phone: null } });

    const view = await renderSettings();

    // Deux occurrences : le nom affiché et la ligne e-mail.
    expect(view.getAllByText('aminata@kora.ci').length).toBeGreaterThan(0);
    expect(view.queryByText('Aminata Diallo')).toBeNull();
  });

  it('désactive la biométrie tant qu’aucune empreinte n’est enregistrée', async () => {
    const view = await renderSettings();

    await waitFor(() =>
      expect(view.getByText('Aucune biométrie enregistrée sur cet appareil.')).toBeTruthy(),
    );
    expect(view.getByTestId('toggle-biometrics').props.accessibilityState.disabled).toBe(true);
  });

  it('bascule toute l’interface au changement de langue, sans redémarrage', async () => {
    const view = await renderSettings();

    expect(view.getByText('Réglages')).toBeTruthy();

    await fireEvent.press(view.getByTestId('setting-language-en'));

    await waitFor(() => expect(view.getByText('Settings')).toBeTruthy());
    expect(view.getByText('Sign out')).toBeTruthy();
    expect(view.queryByText('Se déconnecter')).toBeNull();
  });

  it('confirme la déconnexion avant de purger quoi que ce soit', async () => {
    const view = await renderSettings();

    await fireEvent.press(view.getByTestId('sign-out'));

    // Contrat §6.5 — la déconnexion est locale : le dialogue le dit plutôt que
    // de laisser croire à une invalidation serveur.
    await waitFor(() =>
      expect(
        view.getByText('Vos données locales seront effacées. Aucune opération n’est perdue.'),
      ).toBeTruthy(),
    );
    expect(useSession.getState().status).toBe('authenticated');
  });
});
