import { useCallback, useEffect, useRef } from 'react';
import { BackHandler } from 'react-native';
import { useQueryClient } from '@tanstack/react-query';

import { haptic } from '@/lib/haptics';
import type { CurrencyCode } from '@/lib/money';
import { qk } from '@/lib/queryClient';
import { cashIn, cashOut, transfer } from './api';
import { rememberRecipient, usePaymentFlow } from './flowStore';
import { outcomeForError, outcomeForState, paymentErrorMessage } from './messages';

/**
 * Soumission d'une opération monétaire.
 *
 * ⚠️ **Le backend n'expose aucune clé d'idempotence** (contrat §6.1) : deux
 * requêtes identiques produisent deux débits réels. La protection est
 * intégralement à la charge du client, et elle a **quatre couches** :
 *
 *  1. Un verrou `useRef`, hors du cycle de rendu — c'est la seule couche qui
 *     fait autorité. Un `useState` est asynchrone : deux appuis à 50 ms
 *     d'intervalle passeraient tous les deux avant le re-rendu.
 *  2. Le verrouillage visuel du bouton, qui n'est qu'un signal.
 *  3. `router.replace` plutôt que `push` — la pile ne conserve pas l'étape PIN.
 *  4. La neutralisation du retour matériel et du geste pendant l'exécution.
 *
 * Voir `docs/06-architecture.md` §5.
 */
export function useSubmitPayment() {
  const queryClient = useQueryClient();
  const flow = usePaymentFlow();

  // Couche 1 — le verrou qui fait autorité.
  const lock = useRef(false);

  const submit = useCallback(
    async (rawPin: string) => {
      if (lock.current) return;
      if (!flow.kind || !flow.idempotencyKey) return;

      lock.current = true;
      flow.setSubmitting(true);
      haptic.commit();

      // La devise vient du solde du compte, donc du serveur : elle est une
      // donnée, pas une constante. Le cast est la frontière où on l'assume.
      const amount = {
        minor: flow.amountMinor,
        currency: flow.currency as CurrencyCode,
      };
      const idempotencyKey = flow.idempotencyKey;

      try {
        const receipt =
          flow.kind === 'send'
            ? await transfer({
                rawPin,
                amount,
                paymentMethod: 'WALLET',
                toPhoneNumber: flow.recipientPhone,
                idempotencyKey,
              })
            : flow.kind === 'deposit'
              ? await cashIn({
                  rawPin,
                  amount,
                  paymentMethod: flow.method ?? '',
                  idempotencyKey,
                })
              : await cashOut({
                  rawPin,
                  amount,
                  paymentMethod: flow.method ?? '',
                  idempotencyKey,
                });

        const outcome = outcomeForState(receipt.outcome);

        if (outcome === 'success' && flow.kind === 'send') {
          rememberRecipient(flow.recipientPhone);
        }

        // Le solde et l'historique sont périmés dès qu'une opération aboutit,
        // même partiellement : un état `CAPTURED` a déjà bougé le registre.
        if (outcome !== 'failed') {
          void queryClient.invalidateQueries({ queryKey: qk.balance });
          void queryClient.invalidateQueries({ queryKey: qk.historyAll });
        }

        flow.resolve({ outcome, receipt });
        if (outcome === 'success') haptic.success();
        else haptic.warning();
      } catch (error) {
        const outcome = outcomeForError(error);
        flow.resolve({ outcome, errorMessage: paymentErrorMessage(error), error });

        if (outcome === 'uncertain') {
          // L'argent a peut-être bougé : le cache est suspect dans tous les cas.
          void queryClient.invalidateQueries({ queryKey: qk.balance });
          void queryClient.invalidateQueries({ queryKey: qk.historyAll });
          haptic.warning();
        } else {
          haptic.error();
        }
      } finally {
        // Le PIN est effacé de la portée quelle que soit l'issue — NFR-41.
        lock.current = false;
      }
    },
    [flow, queryClient],
  );

  return { submit, submitting: flow.submitting };
}

/**
 * Couche 4 — neutralise le retour matériel pendant l'exécution.
 *
 * Sans cela, un retour au milieu d'une requête démonterait l'écran PIN, et
 * l'utilisateur pourrait relancer l'opération depuis le récapitulatif alors
 * que la première est encore en vol.
 */
export function useBlockBackWhileSubmitting(submitting: boolean): void {
  useEffect(() => {
    if (!submitting) return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => true);
    return () => subscription.remove();
  }, [submitting]);
}