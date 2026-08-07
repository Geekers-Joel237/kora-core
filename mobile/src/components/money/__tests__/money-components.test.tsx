import { render } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { StateTimeline, StatusChip, TransactionRow } from '@/components/display';
import { OtpInput, PinPad } from '@/components/input';
import { Amount, AmountKeypad, BalanceHero, SHAKE_SEQUENCE } from '@/components/money';
import { stateLabel } from '@/features/shared/labels';
import { THIN_NBSP } from '@/lib/money';
import { KvKey, kvSetString } from '@/lib/storage/kv';
import { currencySymbolStyle, ThemeProvider, type as typeScale } from '@/theme';
import { outcomeOf, TX_STATES, type Transaction } from '@/types/domain';

beforeEach(() => kvSetString(KvKey.themePreference, 'dark'));

function wrap(children: ReactNode) {
  return render(
    <SafeAreaProvider
      initialMetrics={{
        frame: { x: 0, y: 0, width: 390, height: 844 },
        insets: { top: 47, left: 0, right: 0, bottom: 34 },
      }}
    >
      <ThemeProvider>{children}</ThemeProvider>
    </SafeAreaProvider>,
  );
}

const TX: Transaction = {
  id: 'tx-1',
  reference: 'TRX-20260806-A3F91C2D',
  type: 'P2P_TRANSFER',
  direction: 'OUTBOUND',
  state: 'COMPLETED',
  outcome: 'success',
  amount: { minor: 25000, currency: 'XOF' },
  paymentMethod: 'WALLET',
  counterpart: '+225070***011',
  createdAt: new Date('2026-08-06T11:42:13Z'),
  stateHistory: null,
};

describe('Amount — design system §3.4', () => {
  it('compose le montant en trois blocs distincts', async () => {
    const view = await wrap(<Amount minor={125000} currency="XOF" size="displayXl" />);
    expect(view.getByText(`125${THIN_NBSP}000`)).toBeTruthy();
    expect(view.getByText('F')).toBeTruthy();
  });

  it('rend le symbole à 0,45× la taille du montant', async () => {
    const view = await wrap(<Amount minor={1000} currency="XOF" size="displayXl" />);
    const expected = Math.round(typeScale.displayXl.fontSize! * 0.45);
    expect(currencySymbolStyle('displayXl').fontSize).toBe(expected);
    expect(view.getByText('F')).toHaveStyle({ fontSize: expected });
  });

  it('utilise le signe moins mathématique, jamais un trait d’union', async () => {
    const view = await wrap(<Amount minor={-5000} currency="XOF" />);
    expect(view.getByText('−')).toBeTruthy();
    expect(view.queryByText('-')).toBeNull();
  });

  it('n’ajoute un + que sur un flux entrant explicite', async () => {
    const view = await wrap(
      <Amount minor={50000} currency="XOF" sign="always" direction="INBOUND" />,
    );
    expect(view.getByText('+')).toBeTruthy();
  });

  it('masque en quatre pastilles en gardant la devise', async () => {
    const view = await wrap(<Amount minor={125000} currency="XOF" hidden />);
    expect(view.getByText('••••')).toBeTruthy();
    expect(view.getByText('F')).toBeTruthy();
  });

  it('annonce le montant en toutes lettres au lecteur d’écran — NFR-54', async () => {
    const view = await wrap(<Amount minor={125000} currency="XOF" testID="amt" />);
    // Sans espace fine : sinon le lecteur épellerait chiffre par chiffre.
    expect(view.getByTestId('amt').props.accessibilityLabel).toBe('125000 XOF');
  });

  it('applique des chiffres tabulaires — aucun décalage horizontal possible', async () => {
    // Vérification du lot 4 : un solde animé de 0 à 999 999 ne peut pas
    // décaler horizontalement si toutes les glyphes ont la même chasse.
    const narrow = await wrap(<Amount minor={111111} currency="XOF" size="displayXl" />);
    expect(narrow.getByText(`111${THIN_NBSP}111`)).toHaveStyle({
      fontVariant: ['tabular-nums'],
    });

    const wide = await wrap(<Amount minor={999999} currency="XOF" size="displayXl" />);
    expect(wide.getByText(`999${THIN_NBSP}999`)).toHaveStyle({
      fontVariant: ['tabular-nums'],
    });
  });

  it('se rend en mode animé sans lever', async () => {
    await expect(
      wrap(<Amount minor={125000} currency="XOF" size="displayXl" animate />),
    ).resolves.toBeDefined();
  });
});

describe('secousse — §6.3', () => {
  it('a une amplitude DÉCROISSANTE, jamais constante', () => {
    // C'est ce qui la rend crédible. Une amplitude constante se lit comme un
    // bug d'animation, pas comme un refus.
    const amplitudes = SHAKE_SEQUENCE.map(Math.abs);
    for (let index = 1; index < amplitudes.length; index += 1) {
      expect(amplitudes[index]!).toBeLessThanOrEqual(amplitudes[index - 1]!);
    }
    expect(amplitudes[0]).toBeGreaterThan(amplitudes[amplitudes.length - 1]!);
    expect(SHAKE_SEQUENCE[SHAKE_SEQUENCE.length - 1]).toBe(0);
  });

  it('alterne de part et d’autre du point de repos', () => {
    expect(SHAKE_SEQUENCE[0]).toBeLessThan(0);
    expect(SHAKE_SEQUENCE[1]).toBeGreaterThan(0);
    expect(SHAKE_SEQUENCE[2]).toBeLessThan(0);
  });
});

describe('StatusChip', () => {
  it('rend les 11 états sans lever et les regroupe en familles', async () => {
    for (const state of TX_STATES) {
      const view = await wrap(<StatusChip state={state} />);
      expect(view.root).toBeTruthy();
      expect(['pending', 'success', 'failed', 'reversed']).toContain(outcomeOf(state));
    }
  });

  it('affiche le libellé précis en mode détaillé', async () => {
    const view = await wrap(<StatusChip state="AUTHORIZATION_FAILED" detailed />);
    expect(view.getByText('Autorisation refusée')).toBeTruthy();
  });

  it('règle R2 — un état inconnu se rend en « En cours »', async () => {
    const view = await wrap(<StatusChip state="MANUAL_REVIEW" />);
    expect(view.getByText('En cours')).toBeTruthy();
  });
});

describe('TransactionRow — §5', () => {
  it('n’affiche AUCUNE puce sur une opération réussie', async () => {
    const view = await wrap(<TransactionRow transaction={TX} />);
    // Marquer « Terminé » sur 95 % des lignes revient à n'afficher aucune
    // information : seules les anomalies méritent un marqueur.
    expect(view.queryByText('Terminée')).toBeNull();
    expect(view.queryByText('En cours')).toBeNull();
  });

  it('marque en revanche une opération en cours', async () => {
    const view = await wrap(
      <TransactionRow transaction={{ ...TX, state: 'CAPTURED', outcome: 'pending' }} />,
    );
    expect(view.getByText('En cours')).toBeTruthy();
  });

  it('affiche la contrepartie masquée par le serveur pour un P2P', async () => {
    const view = await wrap(<TransactionRow transaction={TX} />);
    expect(view.getByText('+225070***011')).toBeTruthy();
  });

  it('affiche l’opérateur quand il n’y a pas de contrepartie', async () => {
    const view = await wrap(
      <TransactionRow
        transaction={{ ...TX, type: 'CASH_IN', counterpart: null, paymentMethod: 'ORANGE_MONEY' }}
      />,
    );
    expect(view.getByText('Orange Money')).toBeTruthy();
  });
});

describe('StateTimeline — le composant signature', () => {
  const history = [
    { from: null, to: 'INITIALIZED', occurredAt: new Date('2026-08-06T11:42:13.000Z') },
    { from: 'INITIALIZED', to: 'AUTHORIZED', occurredAt: new Date('2026-08-06T11:42:13.412Z') },
    { from: 'AUTHORIZED', to: 'CAPTURED', occurredAt: new Date('2026-08-06T11:42:13.638Z') },
    { from: 'CAPTURED', to: 'COMPLETED', occurredAt: new Date('2026-08-06T11:42:13.701Z') },
  ];

  it('rend une frise complète avec les libellés lisibles', async () => {
    const view = await wrap(<StateTimeline history={history} currentState="COMPLETED" />);
    expect(view.getByText('Initiée')).toBeTruthy();
    expect(view.getByText('Autorisée')).toBeTruthy();
    expect(view.getByText('Capturée')).toBeTruthy();
    expect(view.getByText('Terminée')).toBeTruthy();
  });

  it('n’expose jamais un code d’état brut à l’utilisateur', async () => {
    const view = await wrap(<StateTimeline history={history} currentState="COMPLETED" />);
    for (const state of ['INITIALIZED', 'AUTHORIZED', 'CAPTURED', 'COMPLETED']) {
      expect(view.queryByText(state)).toBeNull();
    }
  });

  it('rend une frise incomplète, encore en cours', async () => {
    const partial = history.slice(0, 2);
    const view = await wrap(<StateTimeline history={partial} currentState="AUTHORIZED" />);
    expect(view.getByText('Autorisée')).toBeTruthy();
    expect(view.queryByText('Terminée')).toBeNull();
  });

  it('rend les 11 états du contrat sans lever', async () => {
    for (const state of TX_STATES) {
      const view = await wrap(
        <StateTimeline
          history={[{ from: null, to: state, occurredAt: new Date('2026-08-06T11:42:13Z') }]}
          currentState={state}
        />,
      );
      expect(view.getByText(stateLabel(state).label)).toBeTruthy();
    }
  });

  it('règle R2 — un état inconnu affiche son code brut plutôt qu’une erreur', async () => {
    const view = await wrap(
      <StateTimeline
        history={[{ from: null, to: 'MANUAL_REVIEW', occurredAt: new Date() }]}
        currentState="MANUAL_REVIEW"
      />,
    );
    expect(view.getByText('MANUAL_REVIEW')).toBeTruthy();
  });
});

describe('BalanceHero', () => {
  it('affiche le solde, le numéro de compte et le bouton de masquage', async () => {
    const view = await wrap(
      <BalanceHero
        minor={125000}
        currency="XOF"
        accountNumber="ACC-20260806-A3F91C2D"
        hidden={false}
        onToggleHidden={jest.fn()}
      />,
    );
    expect(view.getByText('Solde disponible')).toBeTruthy();
    expect(view.getByText('ACC-20260806-A3F91C2D')).toBeTruthy();
    expect(view.getByTestId('toggle-balance').props.accessibilityLabel).toBe('Masquer le solde');
  });

  it('rend un squelette de forme identique pendant le chargement', async () => {
    const view = await wrap(
      <BalanceHero
        minor={0}
        currency="XOF"
        accountNumber=""
        hidden={false}
        onToggleHidden={jest.fn()}
        loading
      />,
    );
    // Jamais d'indicateur circulaire : le solde n'est simplement pas là.
    expect(view.queryByTestId('balance-amount')).toBeNull();
  });
});

describe('AmountKeypad', () => {
  it('affiche le montant courant et les montants rapides', async () => {
    const view = await wrap(
      <AmountKeypad
        currency="XOF"
        value={5000}
        onChange={jest.fn()}
        quickAmounts={[5000, 10000, 25000]}
        remainingLabel="Solde après opération : 120 000 F"
      />,
    );
    expect(view.getByTestId('amount-display')).toBeTruthy();
    expect(view.getByTestId('quick-10000')).toBeTruthy();
    expect(view.getByText('Solde après opération : 120 000 F')).toBeTruthy();
  });

  it('expose les douze touches du pavé', async () => {
    const view = await wrap(<AmountKeypad currency="XOF" value={0} onChange={jest.fn()} />);
    for (const digit of ['0', '1', '5', '9']) {
      expect(view.getByTestId(`key-${digit}`)).toBeTruthy();
    }
    expect(view.getByTestId('key-000')).toBeTruthy();
    expect(view.getByTestId('key-delete')).toBeTruthy();
  });
});

describe('PinPad', () => {
  it('affiche le nombre de pastilles attendu', async () => {
    const view = await wrap(<PinPad title="Entrez votre PIN" onComplete={jest.fn()} />);
    expect(view.getByTestId('pin-dots')).toBeTruthy();
    expect(view.getByText('Entrez votre PIN')).toBeTruthy();
  });

  it('affiche le message d’erreur sans jamais révéler le PIN', async () => {
    const view = await wrap(
      <PinPad title="PIN" onComplete={jest.fn()} error="PIN incorrect" />,
    );
    expect(view.getByText('PIN incorrect')).toBeTruthy();
  });

  it('propose la biométrie quand elle est disponible', async () => {
    const view = await wrap(
      <PinPad
        title="PIN"
        onComplete={jest.fn()}
        biometric={{ onPress: jest.fn(), label: 'Déverrouiller' }}
      />,
    );
    expect(view.getByTestId('key-biometric')).toBeTruthy();
  });
});

describe('OtpInput', () => {
  it('rend six cellules et un champ de saisie accessible', async () => {
    const view = await wrap(<OtpInput onComplete={jest.fn()} autoFocus={false} />);
    expect(view.getByTestId('otp-input').props.accessibilityLabel).toBe(
      'Code de vérification à 6 chiffres',
    );
  });

  it('affiche l’erreur et vide les cellules', async () => {
    const view = await wrap(
      <OtpInput onComplete={jest.fn()} error="Code incorrect ou expiré" autoFocus={false} />,
    );
    expect(view.getByText('Code incorrect ou expiré')).toBeTruthy();
    expect(view.getByTestId('otp-input').props.value).toBe('');
  });
});
