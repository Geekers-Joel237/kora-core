import { toTransaction, toTransactionPage } from '../mappers';
import { transactionHistorySchema, transactionItemSchema } from '../schemas';
import { decode } from '@/lib/decode';
import { isTerminalState, outcomeOf } from '@/types/domain';
import type { ApiTransactionItem } from '@/types/api';

const BASE: ApiTransactionItem = {
  transactionId: '7b2e-0001',
  transactionNumber: 'TRX-20260806-A3F91C2D',
  type: 'P2P_TRANSFER',
  direction: 'OUTBOUND',
  state: 'COMPLETED',
  amount: 25000,
  currency: 'XOF',
  paymentMethod: 'WALLET',
  counterpart: '+225070***011',
  createdAt: '2026-08-06T11:42:13Z',
  stateHistory: null,
};

describe('outcomeOf — règle R2, tolérance à l’inconnu', () => {
  it('regroupe les 11 états en quatre familles', () => {
    expect(outcomeOf('INITIALIZED')).toBe('pending');
    expect(outcomeOf('AUTHORIZED')).toBe('pending');
    expect(outcomeOf('CAPTURED')).toBe('pending');
    expect(outcomeOf('SETTLEMENT_PENDING')).toBe('pending');
    expect(outcomeOf('COMPLETED')).toBe('success');
    expect(outcomeOf('SETTLED')).toBe('success');
    expect(outcomeOf('AUTHORIZATION_FAILED')).toBe('failed');
    expect(outcomeOf('CAPTURE_FAILED')).toBe('failed');
    expect(outcomeOf('SETTLEMENT_FAILED')).toBe('failed');
    expect(outcomeOf('FAILED')).toBe('failed');
    expect(outcomeOf('REVERSED')).toBe('reversed');
  });

  it('replie tout état inconnu sur « en cours », jamais sur « succès »', () => {
    // Les étapes 6 et 8 du ROADMAP en introduiront de nouveaux.
    expect(outcomeOf('MANUAL_REVIEW')).toBe('pending');
    expect(outcomeOf('SOME_FUTURE_STATE')).toBe('pending');
    expect(outcomeOf('')).toBe('pending');
  });

  it('ne considère jamais un état inconnu comme terminal', () => {
    expect(isTerminalState('COMPLETED')).toBe(true);
    expect(isTerminalState('CAPTURED')).toBe(false);
    expect(isTerminalState('MANUAL_REVIEW')).toBe(false);
  });
});

describe('mappeurs — frontière R1', () => {
  it('traduit une opération complète', () => {
    const tx = toTransaction(BASE);
    expect(tx.id).toBe('7b2e-0001');
    expect(tx.reference).toBe('TRX-20260806-A3F91C2D');
    expect(tx.amount).toEqual({ minor: 25000, currency: 'XOF' });
    expect(tx.outcome).toBe('success');
    expect(tx.counterpart).toBe('+225070***011');
    expect(tx.createdAt.toISOString()).toBe('2026-08-06T11:42:13.000Z');
    expect(tx.stateHistory).toBeNull();
  });

  it('traduit la frise des états quand detail=true', () => {
    const tx = toTransaction({
      ...BASE,
      stateHistory: [
        { oldState: null, newState: 'INITIALIZED', occurredAt: '2026-08-06T11:42:13.000Z' },
        {
          oldState: 'INITIALIZED',
          newState: 'AUTHORIZED',
          occurredAt: '2026-08-06T11:42:13.412Z',
        },
      ],
    });
    expect(tx.stateHistory).toHaveLength(2);
    expect(tx.stateHistory?.[0]?.from).toBeNull();
    expect(tx.stateHistory?.[1]?.to).toBe('AUTHORIZED');
  });

  it('ne plante sur aucun état inventé', () => {
    expect(() => toTransaction({ ...BASE, state: 'SOME_FUTURE_STATE' })).not.toThrow();
    expect(toTransaction({ ...BASE, state: 'SOME_FUTURE_STATE' }).outcome).toBe('pending');
  });

  it('ne plante sur aucun type ni direction inconnus', () => {
    const tx = toTransaction({ ...BASE, type: 'MERCHANT_PAYMENT', direction: 'SIDEWAYS' });
    expect(tx.type).toBe('MERCHANT_PAYMENT');
    expect(tx.direction).toBe('OUTBOUND');
  });

  it('encaisse une date illisible sans lever', () => {
    expect(() => toTransaction({ ...BASE, createdAt: 'pas-une-date' })).not.toThrow();
  });
});

describe('schémas — validation permissive', () => {
  it('ignore silencieusement un champ supplémentaire', () => {
    const withExtra = { ...BASE, riskScore: 42, newBackendField: 'valeur' };
    expect(() =>
      decode(transactionItemSchema, withExtra, '/payments/history'),
    ).not.toThrow();
  });

  it('conserve les champs supplémentaires plutôt que de les retirer', () => {
    const parsed = decode(transactionItemSchema, { ...BASE, riskScore: 42 }, '/x') as Record<
      string,
      unknown
    >;
    expect(parsed.riskScore).toBe(42);
  });

  it('signale une dérive quand un champ consommé disparaît', () => {
    const { amount: _removed, ...withoutAmount } = BASE;
    expect(() => decode(transactionItemSchema, withoutAmount, '/payments/history')).toThrow(
      /Réponse inattendue/,
    );
  });

  it('traduit une page complète', () => {
    const page = toTransactionPage(
      decode(
        transactionHistorySchema,
        {
          transactions: [BASE],
          page: 0,
          size: 20,
          totalElements: 143,
          totalPages: 8,
          hasNext: true,
        },
        '/payments/history',
      ),
    );
    expect(page.items).toHaveLength(1);
    expect(page.hasNext).toBe(true);
    expect(page.totalElements).toBe(143);
  });
});
