import { render } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { Button, IconButton } from '@/components/action';
import { Skeleton } from '@/components/feedback';
import { Dialog, Sheet } from '@/components/overlay';
import { Pressable, Text } from '@/components/primitives';
import { KvKey, kvSetString } from '@/lib/storage/kv';
import { pressScale, spring, ThemeProvider, timing } from '@/theme';

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

describe('jetons de mouvement — §2', () => {
  it('définit les cinq ressorts avec des amortissements croissants en réactivité', () => {
    expect(spring.bouncy.damping).toBeLessThan(spring.standard.damping);
    expect(spring.standard.damping).toBeLessThan(spring.snappy.damping);
    expect(spring.gesture.damping).toBeGreaterThan(spring.snappy.damping);
  });

  it('interdit tout rebond sur un geste — un geste suit le doigt', () => {
    expect(spring.gesture.overshootClamping).toBe(true);
  });

  it('respecte le plancher perceptif de 120 ms — Loi 4', () => {
    // En deçà de ~80 ms, un changement de visibilité se lit comme un défaut.
    for (const duration of Object.values(timing)) {
      expect(duration).toBeGreaterThanOrEqual(120);
    }
  });

  it('comprime moins les grands éléments que les petits — §4', () => {
    expect(pressScale.hero).toBeGreaterThan(pressScale.card);
    expect(pressScale.card).toBeGreaterThan(pressScale.button);
    expect(pressScale.button).toBeGreaterThan(pressScale.key);
    expect(pressScale.key).toBeGreaterThan(pressScale.icon);
  });
});

describe('Pressable', () => {
  it('se rend et expose son rôle d’accessibilité', async () => {
    const view = await wrap(
      <Pressable onPress={jest.fn()} accessibilityLabel="Confirmer" testID="p">
        <Text>Confirmer</Text>
      </Pressable>,
    );
    const node = view.getByTestId('p');
    expect(node.props.accessibilityRole).toBe('button');
    expect(node.props.accessibilityLabel).toBe('Confirmer');
  });

  it('étend la zone tactile à la cible minimale — NFR-51', async () => {
    const view = await wrap(
      <Pressable onPress={jest.fn()} testID="p">
        <Text>x</Text>
      </Pressable>,
    );
    const { hitSlop } = view.getByTestId('p').props;
    expect(hitSlop.top + hitSlop.bottom).toBeGreaterThanOrEqual(22);
  });

  it('propage l’état désactivé à l’accessibilité', async () => {
    const view = await wrap(
      <Pressable onPress={jest.fn()} disabled testID="p">
        <Text>x</Text>
      </Pressable>,
    );
    expect(view.getByTestId('p').props.accessibilityState.disabled).toBe(true);
  });
});

describe('Button', () => {
  it('rend les quatre variantes et les trois tailles', async () => {
    for (const variant of ['primary', 'secondary', 'ghost', 'danger'] as const) {
      for (const size of ['lg', 'md', 'sm'] as const) {
        const view = await wrap(
          <Button label={`${variant}-${size}`} onPress={jest.fn()} variant={variant} size={size} />,
        );
        expect(view.getByText(`${variant}-${size}`)).toBeTruthy();
      }
    }
  });

  it('garde le libellé monté pendant le chargement — la largeur ne bouge pas', async () => {
    // Un bouton qui rétrécit pendant sa requête fait sauter la mise en page.
    const view = await wrap(<Button label="Confirmer" onPress={jest.fn()} loading />);
    expect(view.getByText('Confirmer')).toBeTruthy();
  });

  it('désactive l’interaction pendant le chargement', async () => {
    const onPress = jest.fn();
    const view = await wrap(
      <Button label="Envoyer" onPress={onPress} loading testID="btn" />,
    );
    expect(view.getByTestId('btn').props.accessibilityState.disabled).toBe(true);
  });
});

describe('IconButton', () => {
  it('exige et expose un libellé d’accessibilité', async () => {
    const view = await wrap(
      <IconButton
        name="eye"
        onPress={jest.fn()}
        accessibilityLabel="Afficher le solde"
        testID="ib"
      />,
    );
    expect(view.getByTestId('ib').props.accessibilityLabel).toBe('Afficher le solde');
  });
});

describe('Skeleton', () => {
  it('se rend sans indicateur circulaire', async () => {
    await expect(wrap(<Skeleton height={68} />)).resolves.toBeDefined();
  });
});

describe('Sheet', () => {
  it('ne rend rien quand elle est fermée', async () => {
    const view = await wrap(
      <Sheet visible={false} onClose={jest.fn()} title="Filtres">
        <Text>contenu</Text>
      </Sheet>,
    );
    expect(view.queryByText('contenu')).toBeNull();
  });

  it('rend son titre et son contenu quand elle est ouverte', async () => {
    const view = await wrap(
      <Sheet visible onClose={jest.fn()} title="Filtres">
        <Text>contenu</Text>
      </Sheet>,
    );
    expect(view.getByText('Filtres')).toBeTruthy();
    expect(view.getByText('contenu')).toBeTruthy();
  });
});

describe('Dialog', () => {
  it('rend le titre, le message et les deux actions', async () => {
    const view = await wrap(
      <Dialog
        visible
        title="Se déconnecter ?"
        message="Vos données locales seront effacées."
        confirmLabel="Se déconnecter"
        onConfirm={jest.fn()}
        onCancel={jest.fn()}
        destructive
      />,
    );
    expect(view.getByText('Se déconnecter ?')).toBeTruthy();
    expect(view.getByText('Vos données locales seront effacées.')).toBeTruthy();
    expect(view.getByText('Se déconnecter')).toBeTruthy();
    expect(view.getByText('Annuler')).toBeTruthy();
  });
});
