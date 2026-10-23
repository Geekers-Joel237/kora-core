import { Linking, Platform } from 'react-native';

/**
 * Ouverture de l'application de messagerie.
 *
 * **Indispensable ici, pas confortable.** Le second facteur de Kora arrive par
 * e-mail et non par SMS (contrat §1) : `autoComplete="sms-otp"` ne sert à rien,
 * et l'utilisateur doit quitter l'application pour lire son code. Lui épargner
 * la recherche de son client mail est la seule compensation possible.
 *
 * Voir `docs/05-screens.md` §2.4.
 */
export async function openMailbox(): Promise<boolean> {
  const candidates =
    Platform.OS === 'ios'
      ? ['message://', 'googlegmail://', 'ms-outlook://']
      : ['content://com.android.email.provider', 'googlegmail://', 'ms-outlook://'];

  for (const url of candidates) {
    try {
      if (await Linking.canOpenURL(url)) {
        await Linking.openURL(url);
        return true;
      }
    } catch {
      // Un schéma non déclaré lève au lieu de renvoyer `false` : on continue.
    }
  }
  return false;
}
