import { QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react-native';
import type { ReactNode } from 'react';

import { registerTokenProvider } from '@/lib/http';
import { createQueryClient } from '@/lib/queryClient';
import { usePaymentFlow } from '../flowStore';
import { outcomeForError, outcomeForState, paymentErrorMessage } from '../messages';
import { useSubmitPayment } from '../useSubmitPayment';

const RECEIPT = {
  transactionId: 'tx-1',
  transactionNumber: 'TRX-20260806-A3F91C2D',
  state: 'COMPLETED',
  amount: 25000,
  currency: 'XOF',
};

let fetchMock: jest.Mock;

function ok(body: unknown) {
  return { status: 200, ok: true, text: async () => JSON.stringify(body) };
}
function fail(status: number, body: unknown) {
  return { status, ok: false, text: async () => JSON.stringify(body) };
}

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={createQueryClient()}>{children}</QueryClientProvider>;
}

function armFlow() {
  const store = usePaymentFlow.getState();
  store.begin('send', 'XOF');
  store.setAmount(25000);
  store.setRecipient('+2250708091011');
  store.setVerified(true);
  store.armIdempotency();
}

beforeEach(() => {
  fetchMock = jest.fn();
  globalThis.fetch = fetchMock as unknown as typeof fetch;
  registerTokenProvider({
    getAccessToken: () => 'access-1',
    getRefreshToken: () => 'refresh-1',
    onTokensRefreshed: jest.fn(),
    onSessionExpired: jest.fn(),
  });
  usePaymentFlow.getState().reset();
});

afterEach(() => registerTokenProvider(null));

describe('verrou anti-double-débit — LA vérification du lot', () => {
  it('un double appui ne produit QU’UNE SEULE requête', async () => {
    let resolveRequest: ((value: unknown) => void) | null = null;
    fetchMock.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRequest = resolve;
        }),
    );

    armFlow();
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper });

    // Deux appels quasi simultanés, avant tout re-rendu — exactement ce que
    // produit un double appui à 50 ms d'intervalle.
    await act(async () => {
      void result.current.submit('1234');
      void result.current.submit('1234');
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveRequest?.(ok(RECEIPT));
    });
  });

  it('transporte la clé d’idempotence, identique entre deux tentatives', async () => {
    fetchMock.mockImplementation(async () => ok(RECEIPT));

    armFlow();
    const key = usePaymentFlow.getState().idempotencyKey;
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper });

    await act(async () => {
      await result.current.submit('1234');
    });

    const headers = fetchMock.mock.calls[0]?.[1]?.headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBe(key);

    // Rentrer à nouveau dans le récapitulatif ne doit PAS régénérer la clé :
    // sinon un rejeu manuel créerait une seconde opération.
    usePaymentFlow.getState().armIdempotency();
    expect(usePaymentFlow.getState().idempotencyKey).toBe(key);
  });
});

describe('classement des issues — docs §4.6', () => {
  it('présente un état intermédiaire comme EN COURS, jamais comme un succès', async () => {
    // L'écart entre CAPTURED et COMPLETED est exactement le raccourci qui
    // produit une réclamation client.
    fetchMock.mockImplementation(async () => ok({ ...RECEIPT, state: 'CAPTURED' }));

    armFlow();
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper });
    await act(async () => {
      await result.current.submit('1234');
    });

    expect(usePaymentFlow.getState().outcome).toBe('pending');
  });

  it('classe COMPLETED en succès', async () => {
    fetchMock.mockImplementation(async () => ok(RECEIPT));

    armFlow();
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper });
    await act(async () => {
      await result.current.submit('1234');
    });

    expect(usePaymentFlow.getState().outcome).toBe('success');
  });

  it('classe un 503 en ISSUE INCERTAINE, pas en échec', async () => {
    fetchMock.mockImplementation(async () => fail(503, { status: 503 }));

    armFlow();
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper });
    await act(async () => {
      await result.current.submit('1234');
    });

    expect(usePaymentFlow.getState().outcome).toBe('uncertain');
    // Aucune reprise automatique : une seule requête est partie.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('classe une coupure réseau en ISSUE INCERTAINE', async () => {
    fetchMock.mockImplementation(async () => {
      throw new TypeError('Network request failed');
    });

    armFlow();
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper });
    await act(async () => {
      await result.current.submit('1234');
    });

    expect(usePaymentFlow.getState().outcome).toBe('uncertain');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('classe un 500 de fournisseur en ISSUE INCERTAINE', async () => {
    // `ProviderException` n'est mappée par aucun handler — contrat §2.
    fetchMock.mockImplementation(async () =>
      fail(500, { timestamp: '…', status: 500, error: 'Internal Server Error' }),
    );

    armFlow();
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper });
    await act(async () => {
      await result.current.submit('1234');
    });

    expect(usePaymentFlow.getState().outcome).toBe('uncertain');
  });

  it('classe un 422 métier en ÉCHEC, et conserve le montant', async () => {
    fetchMock.mockImplementation(async () =>
      fail(422, { status: 422, detail: 'Insufficient funds for account ACC-1' }),
    );

    armFlow();
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper });
    await act(async () => {
      await result.current.submit('1234');
    });

    const state = usePaymentFlow.getState();
    expect(state.outcome).toBe('failed');
    expect(state.errorMessage).toBe('Solde insuffisant pour cette opération.');
    // Le montant survit : l'utilisateur revient au récapitulatif sans ressaisir.
    expect(state.amountMinor).toBe(25000);
  });
});

describe('traduction des rejets — table de données, pas cascade de if', () => {
  const cases: [number, string, string][] = [
    [422, 'Insufficient funds', 'Solde insuffisant pour cette opération.'],
    [422, 'Self transfer is not allowed', 'Vous ne pouvez pas vous envoyer de l’argent.'],
    [422, 'Currency mismatch: XOF vs EUR', 'Devise incompatible avec votre compte.'],
    [422, 'Account blocked', 'Votre compte est bloqué. Contactez le support.'],
    [422, 'Recipient account is suspended: +225…', 'Le compte du destinataire est suspendu.'],
    // ⚠️ 404, pas 422 — c'est l'erreur de transfert la plus fréquente.
    [404, 'No account found for phone: +225…', 'Aucun compte Kora n’est associé à ce numéro.'],
  ];

  it.each(cases)('traduit %i « %s »', async (status, detail, expected) => {
    fetchMock.mockImplementation(async () => fail(status, { status, detail }));

    armFlow();
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper });
    await act(async () => {
      await result.current.submit('1234');
    });

    expect(usePaymentFlow.getState().errorMessage).toBe(expected);
  });

  it('affiche le detail brut plutôt qu’un message générique sur un motif inconnu', () => {
    const error = {
      status: 422,
      code: 'BUSINESS_RULE' as const,
      message: 'x',
      detail: 'Daily velocity limit exceeded',
      isRetryable: false,
      isAuthExpired: false,
      isOutcomeUnknown: false,
    };
    // L'étape 8 introduira ce motif : d'ici là, une phrase imparfaite vaut
    // mieux que « Une erreur est survenue ».
    expect(paymentErrorMessage(error)).toBe('Daily velocity limit exceeded');
  });
});

describe('machine de parcours', () => {
  it('se réinitialise intégralement à l’entrée', () => {
    armFlow();
    usePaymentFlow.getState().begin('deposit', 'XOF');

    const state = usePaymentFlow.getState();
    expect(state.amountMinor).toBe(0);
    expect(state.recipientPhone).toBe('');
    expect(state.verifiedRecipient).toBe(false);
    expect(state.idempotencyKey).toBeNull();
    expect(state.outcome).toBeNull();
  });

  it('remet la case de vérification à zéro quand le numéro change', () => {
    armFlow();
    expect(usePaymentFlow.getState().verifiedRecipient).toBe(true);

    usePaymentFlow.getState().setRecipient('+2250700000000');
    // Sans cela, changer de destinataire garderait une vérification portant
    // sur un autre numéro — exactement ce que la case est censée empêcher.
    expect(usePaymentFlow.getState().verifiedRecipient).toBe(false);
  });
});

describe('fonctions de classement', () => {
  it('mappe les familles d’état vers les issues du parcours', () => {
    expect(outcomeForState('success')).toBe('success');
    expect(outcomeForState('pending')).toBe('pending');
    expect(outcomeForState('failed')).toBe('failed');
    expect(outcomeForState('reversed')).toBe('failed');
  });

  it('ne rend incertain que ce que la couche HTTP a marqué comme tel', () => {
    expect(
      outcomeForError({
        status: 422,
        code: 'BUSINESS_RULE',
        message: '',
        isRetryable: false,
        isAuthExpired: false,
        isOutcomeUnknown: false,
      }),
    ).toBe('failed');

    expect(
      outcomeForError({
        status: 503,
        code: 'UNAVAILABLE',
        message: '',
        isRetryable: false,
        isAuthExpired: false,
        isOutcomeUnknown: true,
      }),
    ).toBe('uncertain');
  });
});

describe('invalidation du cache', () => {
  it('invalide solde et historique après une opération aboutie', async () => {
    fetchMock.mockImplementation(async () => ok(RECEIPT));

    const client = createQueryClient();
    const spy = jest.spyOn(client, 'invalidateQueries');
    const localWrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );

    armFlow();
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper: localWrapper });
    await act(async () => {
      await result.current.submit('1234');
    });

    await waitFor(() => expect(spy).toHaveBeenCalledTimes(2));
  });

  it('n’invalide rien sur un rejet métier — rien n’a bougé', async () => {
    fetchMock.mockImplementation(async () =>
      fail(422, { status: 422, detail: 'Insufficient funds' }),
    );

    const client = createQueryClient();
    const spy = jest.spyOn(client, 'invalidateQueries');
    const localWrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );

    armFlow();
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper: localWrapper });
    await act(async () => {
      await result.current.submit('1234');
    });

    expect(spy).not.toHaveBeenCalled();
  });

  it('invalide en revanche sur une issue incertaine — le cache est suspect', async () => {
    fetchMock.mockImplementation(async () => fail(503, { status: 503 }));

    const client = createQueryClient();
    const spy = jest.spyOn(client, 'invalidateQueries');
    const localWrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );

    armFlow();
    const { result } = await renderHook(() => useSubmitPayment(), { wrapper: localWrapper });
    await act(async () => {
      await result.current.submit('1234');
    });

    await waitFor(() => expect(spy).toHaveBeenCalledTimes(2));
  });
});