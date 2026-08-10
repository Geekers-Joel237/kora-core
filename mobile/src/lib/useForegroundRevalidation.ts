import { useEffect, useRef } from 'react';
import { AppState, type AppStateStatus } from 'react-native';
import { useQueryClient } from '@tanstack/react-query';

import { LOCK_AFTER_MS, lockApp } from '@/features/auth/biometrics';
import { qk } from '@/lib/queryClient';

/**
 * Revalidation au retour au premier plan — `docs/05-screens.md` §8.3.
 *
 * | Délai depuis la mise en arrière-plan | Comportement |
 * |---|---|
 * | < 30 s | Rien — l'utilisateur a juste consulté une notification |
 * | 30 s – 5 min | Revalidation du solde et de l'historique |
 * | > 5 min | Revalidation + verrouillage biométrique |
 *
 * Revalider systématiquement produirait deux requêtes à chaque bascule
 * d'application, ce qui grèverait le budget data de `NFR-25`.
 */
const REVALIDATE_AFTER_MS = 30_000;

export function useForegroundRevalidation(): void {
  const queryClient = useQueryClient();
  const backgroundedAt = useRef<number | null>(null);

  useEffect(() => {
    const handle = (state: AppStateStatus) => {
      if (state === 'background' || state === 'inactive') {
        backgroundedAt.current ??= Date.now();
        return;
      }

      if (state !== 'active') return;

      const since = backgroundedAt.current;
      backgroundedAt.current = null;
      if (since === null) return;

      const away = Date.now() - since;

      // Le verrou passe avant la revalidation : une requête lancée derrière un
      // écran verrouillé afficherait des données que l'utilisateur n'a pas
      // encore le droit de voir.
      if (away >= LOCK_AFTER_MS) lockApp();

      if (away < REVALIDATE_AFTER_MS) return;

      void queryClient.invalidateQueries({ queryKey: qk.balance });
      void queryClient.invalidateQueries({ queryKey: qk.historyAll });
    };

    const subscription = AppState.addEventListener('change', handle);
    return () => subscription.remove();
  }, [queryClient]);
}
