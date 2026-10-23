import * as LocalAuthentication from 'expo-local-authentication';

import {
  lockApp,
  probeBiometrics,
  setBiometricsEnabled,
  unlockApp,
  useBiometrics,
} from '../biometrics';
import { useSession } from '../session';

const TOKENS = {
  accessToken: 'access-1',
  accessTokenExpiry: new Date(Date.now() + 60_000),
  refreshToken: 'refresh-1',
  refreshTokenExpiry: new Date(Date.now() + 600_000),
};

const hasHardware = LocalAuthentication.hasHardwareAsync as jest.Mock;
const isEnrolled = LocalAuthentication.isEnrolledAsync as jest.Mock;
const authenticate = LocalAuthentication.authenticateAsync as jest.Mock;

beforeEach(() => {
  jest.clearAllMocks();
  useBiometrics.setState({ enabled: false, available: false, locked: false });
  useSession.setState({
    status: 'authenticated',
    tokens: TOKENS,
    user: { id: 'u1', email: 'aminata@kora.ci', role: 'CUSTOMER' },
    expired: false,
    resumePath: null,
  });
});

describe('session expirée — docs/05-screens.md §8.1', () => {
  it('n’éjecte pas l’utilisateur : le statut reste authentifié', async () => {
    await useSession.getState().markExpired('/send/review');

    // L'interdit du §8.1 : perdre le parcours en repassant en « anonymous »
    // renverrait quelqu'un au récapitulatif d'un transfert vers l'accueil.
    expect(useSession.getState().status).toBe('authenticated');
    expect(useSession.getState().expired).toBe(true);
    expect(useSession.getState().resumePath).toBe('/send/review');
  });

  it('efface les jetons morts sans toucher au profil', async () => {
    useSession.setState({ profile: { fullName: 'Aminata Diallo', phone: '+2250708091011' } });

    await useSession.getState().markExpired(null);

    expect(useSession.getState().tokens).toBeNull();
    expect(useSession.getState().profile.fullName).toBe('Aminata Diallo');
  });

  it('ne réécrit pas le chemin de reprise sur une seconde expiration', async () => {
    await useSession.getState().markExpired('/send/review');
    await useSession.getState().markExpired('/home');

    // Deux requêtes en vol reçoivent le même `401` : la seconde ne doit pas
    // écraser l'écran réellement quitté.
    expect(useSession.getState().resumePath).toBe('/send/review');
  });

  it('ne rend le chemin de reprise qu’une seule fois', async () => {
    await useSession.getState().markExpired('/send/review');

    expect(useSession.getState().consumeResumePath()).toBe('/send/review');
    expect(useSession.getState().consumeResumePath()).toBeNull();
  });

  it('lève l’expiration dès l’adoption de nouveaux jetons', async () => {
    await useSession.getState().markExpired('/home');
    await useSession.getState().adopt(TOKENS);

    expect(useSession.getState().expired).toBe(false);
    expect(useSession.getState().tokens).toEqual(TOKENS);
  });

  it('remet tout à zéro sur une déconnexion explicite', async () => {
    await useSession.getState().markExpired('/home');
    await useSession.getState().signOut();

    expect(useSession.getState().status).toBe('anonymous');
    expect(useSession.getState().expired).toBe(false);
    expect(useSession.getState().resumePath).toBeNull();
    expect(useSession.getState().profile).toEqual({ fullName: null, phone: null });
  });
});

describe('verrouillage biométrique — docs/05-screens.md §8.3', () => {
  it('n’est disponible qu’avec matériel ET empreinte enregistrée', async () => {
    hasHardware.mockResolvedValue(true);
    isEnrolled.mockResolvedValue(false);
    expect(await probeBiometrics()).toBe(false);

    hasHardware.mockResolvedValue(false);
    isEnrolled.mockResolvedValue(true);
    expect(await probeBiometrics()).toBe(false);

    hasHardware.mockResolvedValue(true);
    isEnrolled.mockResolvedValue(true);
    expect(await probeBiometrics()).toBe(true);
  });

  it('ne verrouille jamais si l’option est active mais la biométrie absente', () => {
    setBiometricsEnabled(true);
    useBiometrics.setState({ available: false });

    lockApp();

    // Sinon une empreinte retirée depuis l'installation enfermerait dehors.
    expect(useBiometrics.getState().locked).toBe(false);
  });

  it('verrouille quand l’option est active et la biométrie disponible', () => {
    setBiometricsEnabled(true);
    useBiometrics.setState({ available: true });

    lockApp();

    expect(useBiometrics.getState().locked).toBe(true);
  });

  it('lève le verrou dès que l’option est désactivée', () => {
    useBiometrics.setState({ enabled: true, available: true, locked: true });

    setBiometricsEnabled(false);

    expect(useBiometrics.getState().locked).toBe(false);
  });

  it('libère l’appareil devenu incapable de biométrie', async () => {
    useBiometrics.setState({ enabled: true, available: true, locked: true });
    hasHardware.mockResolvedValue(true);
    isEnrolled.mockResolvedValue(false);

    await probeBiometrics();

    expect(useBiometrics.getState().locked).toBe(false);
  });

  it('ne lève le verrou qu’en cas d’authentification réussie', async () => {
    useBiometrics.setState({ enabled: true, available: true, locked: true });

    authenticate.mockResolvedValue({ success: false, error: 'user_cancel' });
    expect(await unlockApp()).toBe(false);
    expect(useBiometrics.getState().locked).toBe(true);

    authenticate.mockResolvedValue({ success: true });
    expect(await unlockApp()).toBe(true);
    expect(useBiometrics.getState().locked).toBe(false);
  });

  it('autorise le repli sur le code de l’appareil', async () => {
    authenticate.mockResolvedValue({ success: true });
    await unlockApp();

    // Refuser ce repli enfermerait dehors un capteur qui ne répond pas, sans
    // rien protéger de plus : le code protège déjà le trousseau.
    expect(authenticate).toHaveBeenCalledWith(
      expect.objectContaining({ disableDeviceFallback: false }),
    );
  });

  it('ne plante pas si la couche native lève', async () => {
    hasHardware.mockRejectedValue(new Error('no module'));
    expect(await probeBiometrics()).toBe(false);

    authenticate.mockRejectedValue(new Error('no module'));
    expect(await unlockApp()).toBe(false);
  });
});
