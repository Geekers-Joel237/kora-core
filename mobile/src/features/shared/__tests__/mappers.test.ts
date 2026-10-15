import { toTransaction, toTransactionPage } from '../mappers';
import { transactionHistorySchema, transactionItemSchema } from '../schemas';
import { decode } from '@/lib/decode';
import { outcomeOf, outcomeOfStatus } from '@/types/domain';
import type { ApiTransactionItem } from '@/types/api';

const BASE: ApiTransactionItem = {
  reference: 'TRX-20260806-A3F91C2D',
  kind: 'TRANSFER_SENT',
  status: 'COMPLETED',
  amount: -25000,
  currency: 'XOF',
  counterpart: '+225070***011',
  channel: null,
  date: '2026-08-06T11:42:13Z',
};

describe('outcomeOfStatus — le statut client de l’historique', () => {
  it('rend chacun des quatre statuts dans sa famille', () => {
    expect(outcomeOfStatus('PENDING')).toBe('pending');
    expect(outcomeOfStatus('COMPLETED')).toBe('success');
    expect(outcomeOfStatus('FAILED')).toBe('failed');
    expect(outcomeOfStatus('REFUNDED')).toBe('reversed');
  });

  it('replie un statut inconnu sur « en cours », jamais sur « succès »', () => {
    expect(outcomeOfStatus('MANUAL_REVIEW')).toBe('pending');
    expect(outcomeOfStatus('')).toBe('pending');
  });
});

describe('outcomeOf — l’état technique du reçu, aligné sur le backend', () => {
  it('ne montre « en cours » qu’avant la capture', () => {
    expect(outcomeOf('INITIALIZED')).toBe('pending');
    expect(outcomeOf('AUTHORIZED')).toBe('pending');
    expect(outcomeOf('UNKNOWN')).toBe('pending');
  });

  it('montre « succès » dès la capture, règlement compris', () => {
    expect(outcomeOf('CAPTURED')).toBe('success');
    expect(outcomeOf('SETTLEMENT_PENDING')).toBe('success');
    expect(outcomeOf('SETTLED')).toBe('success');
    expect(outcomeOf('COMPLETED')).toBe('success');
    // Le règlement se joue entre Kora et l'opérateur : l'argent du client a bougé.
    expect(outcomeOf('SETTLEMENT_FAILED')).toBe('success');
  });

  it('montre « échec » quand rien n’a bougé, « remboursé » après une contrepassation', () => {
    expect(outcomeOf('AUTHORIZATION_FAILED')).toBe('failed');
    expect(outcomeOf('CAPTURE_FAILED')).toBe('failed');
    expect(outcomeOf('FAILED')).toBe('failed');
    expect(outcomeOf('REVERSED')).toBe('reversed');
  });

  it('replie tout état inconnu sur « en cours »', () => {
    expect(outcomeOf('MANUAL_REVIEW')).toBe('pending');
    expect(outcomeOf('')).toBe('pending');
  });
});

describe('mappeurs — frontière R1', () => {
  it('traduit une opération complète', () => {
    const tx = toTransaction(BASE);
    expect(tx.reference).toBe('TRX-20260806-A3F91C2D');
    expect(tx.kind).toBe('TRANSFER_SENT');
    expect(tx.outcome).toBe('success');
    expect(tx.counterpart).toBe('+225070***011');
    expect(tx.channel).toBeNull();
    expect(tx.createdAt.toISOString()).toBe('2026-08-06T11:42:13.000Z');
  });

  it('garde le montant positif et porte le signe dans le sens', () => {
    const sent = toTransaction(BASE);
    expect(sent.amount).toEqual({ minor: 25000, currency: 'XOF' });
    expect(sent.direction).toBe('OUTBOUND');

    const deposit = toTransaction({ ...BASE, kind: 'DEPOSIT', amount: 10000, channel: 'ORANGE_MONEY' });
    expect(deposit.amount).toEqual({ minor: 10000, currency: 'XOF' });
    expect(deposit.direction).toBe('INBOUND');
    expect(deposit.channel).toBe('ORANGE_MONEY');
  });

  it('ne plante sur aucun statut ni aucune nature inventés', () => {
    const tx = toTransaction({ ...BASE, status: 'SOME_FUTURE_STATUS', kind: 'MERCHANT_PAYMENT' });
    expect(tx.outcome).toBe('pending');
    expect(tx.kind).toBe('MERCHANT_PAYMENT');
    // Le signe fait foi même pour une nature inconnue.
    expect(tx.direction).toBe('OUTBOUND');
  });

  it('encaisse une date illisible sans lever', () => {
    expect(() => toTransaction({ ...BASE, date: 'pas-une-date' })).not.toThrow();
  });
});

describe('schémas — validation permissive', () => {
  it('ignore silencieusement un champ supplémentaire', () => {
    const withExtra = { ...BASE, riskScore: 42, newBackendField: 'valeur' };
    expect(() => decode(transactionItemSchema, withExtra, '/payments/history')).not.toThrow();
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

  it('traduit une page et garde le curseur qui l’a produite', () => {
    const page = toTransactionPage(
      decode(
        transactionHistorySchema,
        { items: [BASE], nextCursor: 'opaque-next' },
        '/payments/history',
      ),
      'opaque-current',
    );
    expect(page.items).toHaveLength(1);
    expect(page.cursor).toBe('opaque-current');
    expect(page.nextCursor).toBe('opaque-next');
  });

  it('accepte une dernière page sans curseur suivant', () => {
    const page = toTransactionPage(
      decode(transactionHistorySchema, { items: [], nextCursor: null }, '/payments/history'),
      null,
    );
    expect(page.items).toEqual([]);
    expect(page.nextCursor).toBeNull();
  });
});
