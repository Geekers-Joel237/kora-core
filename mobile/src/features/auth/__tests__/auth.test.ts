import {
  CREDENTIALS_MESSAGE,
  loginErrorMessage,
  otpErrorMessage,
  registerErrorMessage,
} from '../messages';
import {
  beginOtpFlow,
  endOtpFlow,
  maskEmail,
  otpCredentials,
  otpEmail,
  otpOrigin,
} from '../otpFlow';
import { normalizeHttpError, normalizeTransportError } from '@/lib/http';

const AUTH = { path: '/auth/login', isMoneyMovement: false };

describe('messages d’authentification — l’invariant de sécurité du lot', () => {
  it('rend EXACTEMENT le même message pour un e-mail inconnu et un PIN erroné', () => {
    // Le backend distingue les deux : 404 pour l'e-mail, 401 pour le PIN.
    // Propager cette distinction offrirait un oracle d'énumération de comptes.
    const unknownEmail = normalizeHttpError(404, { status: 404, detail: 'Customer not found' }, AUTH);
    const wrongPin = normalizeHttpError(401, { status: 401, detail: 'Invalid PIN' }, AUTH);

    expect(loginErrorMessage(unknownEmail)).toBe(CREDENTIALS_MESSAGE);
    expect(loginErrorMessage(wrongPin)).toBe(CREDENTIALS_MESSAGE);
    expect(loginErrorMessage(unknownEmail)).toBe(loginErrorMessage(wrongPin));
  });

  it('ne laisse fuiter aucun détail serveur dans le message de connexion', () => {
    const error = normalizeHttpError(
      404,
      { status: 404, detail: 'Customer not found: aminata@kora.ci' },
      AUTH,
    );
    expect(loginErrorMessage(error)).not.toContain('aminata@kora.ci');
    expect(loginErrorMessage(error)).not.toContain('not found');
  });

  it('distingue en revanche les incidents techniques', () => {
    const unavailable = normalizeHttpError(503, { status: 503 }, AUTH);
    expect(loginErrorMessage(unavailable)).toContain('indisponible');

    const offline = normalizeTransportError(new Error('offline'), AUTH);
    expect(loginErrorMessage(offline)).toContain('connexion');
  });

  it('traite un OTP faux, expiré ou réutilisé de la même façon', () => {
    // Le backend supprime le code dès la première vérification réussie :
    // les trois cas produisent un 401 indiscernable.
    for (const detail of [
      'OTP code does not match',
      'OTP expired or already consumed for: aminata@kora.ci',
      'OTP not found for: aminata@kora.ci',
    ]) {
      const error = normalizeHttpError(401, { status: 401, detail }, AUTH);
      expect(otpErrorMessage(error)).toBe('Code incorrect ou expiré.');
    }
  });

  it('traduit le conflit d’e-mail à l’inscription', () => {
    const error = normalizeHttpError(409, { status: 409, detail: 'Email already registered' }, AUTH);
    expect(registerErrorMessage(error)).toBe('Cet e-mail est déjà utilisé.');
  });

  it('traduit un échec d’envoi de mail en incident réessayable', () => {
    const error = normalizeHttpError(503, { status: 503, detail: 'Mail delivery failed' }, AUTH);
    expect(registerErrorMessage(error)).toContain('Réessayez');
  });
});

describe('parcours OTP — NFR-41', () => {
  afterEach(endOtpFlow);

  it('conserve les identifiants uniquement pendant le parcours', () => {
    beginOtpFlow({ origin: 'register', email: 'aminata@kora.ci', rawPin: '1234' });

    expect(otpEmail()).toBe('aminata@kora.ci');
    expect(otpOrigin()).toBe('register');
    expect(otpCredentials()).toEqual({ email: 'aminata@kora.ci', rawPin: '1234' });
  });

  it('efface le PIN dès la sortie du parcours', () => {
    beginOtpFlow({ origin: 'login', email: 'a@b.ci', rawPin: '1234' });
    endOtpFlow();

    // Le PIN ne survit à rien : ni au succès, ni à l'abandon, ni au démontage.
    expect(otpCredentials()).toBeNull();
    expect(otpEmail()).toBeNull();
    expect(otpOrigin()).toBeNull();
  });

  it('ne rend aucun identifiant hors d’un parcours actif', () => {
    expect(otpCredentials()).toBeNull();
  });
});

describe('masquage de l’e-mail', () => {
  it('ne laisse voir que la première lettre et le domaine', () => {
    expect(maskEmail('aminata@kora.ci')).toBe('a•••@kora.ci');
    expect(maskEmail('k@kora.ci')).toBe('k•••@kora.ci');
  });

  it('encaisse une chaîne qui n’est pas un e-mail', () => {
    expect(maskEmail('pas-un-email')).toBe('pas-un-email');
  });
});
