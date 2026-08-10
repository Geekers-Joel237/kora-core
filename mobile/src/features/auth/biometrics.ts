/**
 * Déverrouillage biométrique — FR-51 *(P1)*, `docs/05-screens.md` §8.3.
 *
 * **La biométrie ne remplace jamais le PIN.** Le backend exige `rawPin` sur
 * chaque opération monétaire (contrat §2) : aucune empreinte ne peut s'y
 * substituer. Elle ne protège que l'accès à l'application au retour au premier
 * plan, ce qui est exactement ce que la plateforme sait garantir.
 *
 * Rien n'est stocké : ni jeton, ni PIN, ni secret dérivé. La seule donnée
 * persistée est le booléen « l'utilisateur a activé cette option ».
 */

import * as LocalAuthentication from 'expo-local-authentication';
import { create } from 'zustand';

import { t } from '@/i18n';
import { KvKey, kvGetBoolean, kvSetBoolean } from '@/lib/storage/kv';

/** Au-delà de ce délai en arrière-plan, l'application se reverrouille — §8.3. */
export const LOCK_AFTER_MS = 5 * 60 * 1000;

interface BiometricsState {
  /** Préférence utilisateur, persistée. */
  enabled: boolean;
  /** Matériel présent **et** au moins une empreinte enregistrée. */
  available: boolean;
  /** Verrou actif : l'application est masquée tant qu'il n'est pas levé. */
  locked: boolean;
}

function readEnabled(): boolean {
  try {
    return kvGetBoolean(KvKey.biometricsEnabled);
  } catch {
    return false;
  }
}

export const useBiometrics = create<BiometricsState>(() => ({
  enabled: readEnabled(),
  available: false,
  locked: false,
}));

/**
 * Sonde le matériel. Le résultat n'est pas mis en cache entre deux lancements :
 * une empreinte peut être ajoutée ou retirée pendant que l'app est installée.
 */
export async function probeBiometrics(): Promise<boolean> {
  try {
    const [hardware, enrolled] = await Promise.all([
      LocalAuthentication.hasHardwareAsync(),
      LocalAuthentication.isEnrolledAsync(),
    ]);
    const available = hardware && enrolled;
    // Une option activée puis devenue impossible ne doit pas verrouiller
    // définitivement l'application : elle se désactive d'elle-même.
    useBiometrics.setState({ available, ...(available ? {} : { locked: false }) });
    return available;
  } catch {
    useBiometrics.setState({ available: false, locked: false });
    return false;
  }
}

export function setBiometricsEnabled(enabled: boolean): void {
  kvSetBoolean(KvKey.biometricsEnabled, enabled);
  useBiometrics.setState({ enabled, ...(enabled ? {} : { locked: false }) });
}

export function lockApp(): void {
  const { enabled, available } = useBiometrics.getState();
  if (enabled && available) useBiometrics.setState({ locked: true });
}

/**
 * Demande la biométrie et lève le verrou en cas de succès.
 *
 * `disableDeviceFallback: false` laisse le code de l'appareil prendre le
 * relais : refuser ce repli enfermerait dehors quelqu'un dont le capteur ne
 * répond pas, sans aucun gain de sécurité — le code de l'appareil protège déjà
 * le trousseau où vivent les jetons.
 */
export async function unlockApp(): Promise<boolean> {
  try {
    const result = await LocalAuthentication.authenticateAsync({
      promptMessage: t('settings.biometricsPrompt'),
      disableDeviceFallback: false,
    });
    if (result.success) useBiometrics.setState({ locked: false });
    return result.success;
  } catch {
    return false;
  }
}
