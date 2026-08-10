/**
 * Déclencheurs du panneau — `docs/10-validation-mode.md` §2.
 *
 * | Méthode | Contexte |
 * |---|---|
 * | Secousse de l'appareil | Ouvre le panneau depuis n'importe quel écran |
 * | Appui long 3 s sur le logo de l'accueil | Alternative sans capteur |
 * | Appui triple sur le numéro de version dans les réglages | Alternative discrète |
 *
 * **Sur la secousse.** Le §2 la décrit comme le geste principal. Elle passe ici
 * par le menu développeur de React Native, que la secousse ouvre nativement :
 * `DevSettings.addMenuItem` y ajoute une entrée Kora. Un vrai détecteur de
 * secousse exigerait `expo-sensors`, l'accéléromètre en continu, et une
 * reconstruction native — pour économiser un appui.
 * `CONTOURNEMENT(indéterminé)`.
 *
 * Les deux autres déclencheurs sont exacts : `logoLongPress` est câblé sur
 * l'appui long de la salutation d'accueil, `versionTap` sur le numéro de
 * version des réglages.
 */

import { DevSettings } from 'react-native';

import { openDevtools } from './panel/store';

/** Fenêtre pendant laquelle trois appuis comptent comme un appui triple. */
const TRIPLE_TAP_WINDOW_MS = 700;
const TRIPLE_TAP_COUNT = 3;

let taps = 0;
let tapTimer: ReturnType<typeof setTimeout> | null = null;

/** Appui long sur le logo — la durée est portée par le `Pressable` appelant. */
function logoLongPress(): void {
  openDevtools();
}

/** Appui sur le numéro de version. Trois en moins de 700 ms ouvrent le panneau. */
function versionTap(): void {
  taps += 1;

  if (tapTimer !== null) clearTimeout(tapTimer);
  tapTimer = setTimeout(() => {
    taps = 0;
    tapTimer = null;
  }, TRIPLE_TAP_WINDOW_MS);

  if (taps >= TRIPLE_TAP_COUNT) {
    taps = 0;
    if (tapTimer !== null) clearTimeout(tapTimer);
    tapTimer = null;
    openDevtools();
  }
}

export const devtoolsTrigger = { logoLongPress, versionTap };

let menuInstalled = false;

/**
 * Installe l'entrée du menu développeur, atteinte par la secousse.
 *
 * `addMenuItem` n'existe qu'en développement ; en staging, les deux autres
 * déclencheurs prennent le relais.
 */
export function installShakeTrigger(): void {
  if (menuInstalled) return;
  menuInstalled = true;

  try {
    DevSettings.addMenuItem('Kora — mode validation', () => openDevtools());
  } catch {
    // Menu développeur indisponible : rien à installer, rien à signaler.
  }
}
