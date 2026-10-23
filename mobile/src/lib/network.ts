import NetInfo from '@react-native-community/netinfo';
import { create } from 'zustand';

/**
 * État réseau — `docs/06-architecture.md` §7.1.
 *
 * Le bandeau hors-ligne n'apparaît qu'après **2 secondes** de déconnexion :
 * en deçà, un micro-changement d'état produirait un clignotement plus gênant
 * que l'information qu'il porte.
 */
const OFFLINE_GRACE_MS = 2000;

interface NetworkState {
  /** État instantané, tel que rapporté par le système. */
  connected: boolean;
  /** État stabilisé — le seul que l'interface doit consommer. */
  offline: boolean;
}

export const useNetwork = create<NetworkState>(() => ({
  connected: true,
  offline: false,
}));

let graceTimer: ReturnType<typeof setTimeout> | null = null;

/** Installé une fois au démarrage. Renvoie la fonction de désabonnement. */
export function startNetworkWatch(): () => void {
  return NetInfo.addEventListener((state) => {
    // `isInternetReachable` vaut `null` tant que la sonde n'a pas abouti :
    // le traiter comme « hors ligne » ferait clignoter le bandeau au lancement.
    const connected = state.isConnected === true && state.isInternetReachable !== false;

    useNetwork.setState({ connected });

    if (graceTimer) {
      clearTimeout(graceTimer);
      graceTimer = null;
    }

    if (connected) {
      useNetwork.setState({ offline: false });
      return;
    }

    graceTimer = setTimeout(() => {
      useNetwork.setState({ offline: true });
    }, OFFLINE_GRACE_MS);
  });
}
