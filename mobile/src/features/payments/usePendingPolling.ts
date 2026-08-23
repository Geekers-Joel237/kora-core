import { useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';

import { getHistory } from '@/features/history/api';
import { isTerminalState, type Transaction } from '@/types/domain';

/**
 * Suivi d'une opération en cours — contrat §6.4.
 *
 * **Aucun canal temps réel n'existe** : ni webhook, ni WebSocket, ni push. Une
 * opération laissée en `pending` ne signalera jamais son achèvement d'elle-même.
 *
 * Le sondage est donc borné, et strictement : 5 s d'intervalle, 12 tentatives
 * au plus (une minute), arrêt immédiat dès que l'application passe en
 * arrière-plan. Sonder au-delà grèverait la batterie et le budget data pour un
 * gain nul.
 *
 * `CONTOURNEMENT(étape-5)` — supprimé le jour où un canal d'événements existe.
 */
const INTERVAL_MS = 5000;
const MAX_ATTEMPTS = 12;
const PAGE_SIZE = 10;

export function usePendingPolling(transactionId: string | null, active: boolean) {
  const [state, setState] = useState<string | null>(null);
  const attempts = useRef(0);

  useEffect(() => {
    if (!transactionId || !active) return;

    attempts.current = 0;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | null = null;

    const stop = () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };

    const tick = async () => {
      if (cancelled || attempts.current >= MAX_ATTEMPTS) return;
      attempts.current += 1;

      try {
        const page = await getHistory({ size: PAGE_SIZE });
        const found: Transaction | undefined = page.items.find((tx) => tx.id === transactionId);

        if (found && !cancelled) {
          setState(found.state);
          if (isTerminalState(found.state)) return;
        }
      } catch {
        // Un échec de sondage n'est pas une erreur d'écran : l'opération suit
        // son cours côté serveur, et l'utilisateur voit toujours « en cours ».
      }

      if (!cancelled) timer = setTimeout(() => void tick(), INTERVAL_MS);
    };

    timer = setTimeout(() => void tick(), INTERVAL_MS);

    // L'arrêt en arrière-plan est explicite : un sondage qui survit à la mise
    // en veille draine la batterie sans que personne ne regarde le résultat.
    const subscription = AppState.addEventListener('change', (next) => {
      if (next !== 'active') stop();
    });

    return () => {
      stop();
      subscription.remove();
    };
  }, [transactionId, active]);

  return state;
}