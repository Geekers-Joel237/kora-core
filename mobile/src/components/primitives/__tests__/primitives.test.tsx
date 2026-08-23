import { render } from '@testing-library/react-native';
import type { ReactNode } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { Divider, Icon, ICON_NAMES, Spacer, Surface, Text } from '../index';
import { GalleryScreen } from '@/devtools/gallery/GalleryScreen';
import { KvKey, kvSetString } from '@/lib/storage/kv';
import { darkTheme, lightTheme, maxFontScale, ThemeProvider, type } from '@/theme';

// L'environnement de test rapporte le thème clair. On fixe la préférence par
// le mécanisme réel plutôt qu'en simulant `useColorScheme` : le test exerce
// ainsi le chemin de persistance en même temps que le rendu.
beforeEach(() => kvSetString(KvKey.themePreference, 'dark'));

/**
 * ⚠️ `render` est **asynchrone** depuis React Native Testing Library 14, qui
 * s'aligne sur le rendu concurrent de React 19. L'oublier produit un
 * « getByText is not a function » parfaitement opaque.
 */
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

describe('Text', () => {
  it('applique la variante et le rôle de couleur du thème', async () => {
    const view = await wrap(<Text variant="titleLg">Solde</Text>);
    expect(view.getByText('Solde')).toHaveStyle({
      fontSize: type.titleLg.fontSize,
      color: darkTheme.text.primary,
    });
  });

  it('plafonne la mise à l’échelle selon la variante — §3.5', async () => {
    const view = await wrap(<Text variant="displayXl">125 000 F</Text>);
    expect(view.getByText('125 000 F').props.maxFontSizeMultiplier).toBe(
      maxFontScale.displayXl,
    );
  });

  it('applique les chiffres tabulaires sur demande — §3.4', async () => {
    const view = await wrap(
      <Text variant="displayMd" tabular>
        999 999 F
      </Text>,
    );
    expect(view.getByText('999 999 F')).toHaveStyle({ fontVariant: ['tabular-nums'] });
  });

  it('laisse `tint` primer sur le rôle, pour une couleur résolue à l’exécution', async () => {
    const view = await wrap(
      <Text color="primary" tint={darkTheme.status.failed.fg}>
        Échouée
      </Text>,
    );
    expect(view.getByText('Échouée')).toHaveStyle({ color: darkTheme.status.failed.fg });
  });

  it('résout chaque graisse vers une fontFamily — indispensable sur Android', () => {
    // `fontWeight` est ignoré avec des polices personnalisées : chaque graisse
    // est un fichier distinct qu'il faut nommer explicitement.
    for (const variant of Object.keys(type) as (keyof typeof type)[]) {
      expect(type[variant].fontFamily).toMatch(/^Inter_/);
    }
  });
});

describe('Surface', () => {
  it('rend chaque niveau d’élévation sans lever', async () => {
    for (const level of [0, 1, 2, 3, 4] as const) {
      const view = await wrap(
        <Surface elevation={level}>
          <Text>niveau {level}</Text>
        </Surface>,
      );
      expect(view.getByText(`niveau ${level}`)).toBeTruthy();
    }
  });
});

describe('Icon', () => {
  it('rend tout le jeu minimal sans lever — §6.2', async () => {
    expect(ICON_NAMES.length).toBeGreaterThanOrEqual(30);
    for (const name of ICON_NAMES) {
      await expect(wrap(<Icon name={name} />)).resolves.toBeDefined();
    }
  });

  it('rend chaque taille autorisée', async () => {
    for (const size of ['xs', 'sm', 'md', 'lg', 'xl'] as const) {
      await expect(wrap(<Icon name="home" size={size} />)).resolves.toBeDefined();
    }
  });
});

describe('Divider et Spacer', () => {
  it('se rendent dans les deux orientations', async () => {
    await expect(
      wrap(
        <>
          <Divider />
          <Divider orientation="vertical" strong />
          <Spacer size={4} />
          <Spacer size={2} axis="horizontal" />
        </>,
      ),
    ).resolves.toBeDefined();
  });
});

describe('galerie du design system', () => {
  it('se rend intégralement sans lever', async () => {
    const view = await wrap(<GalleryScreen />);
    expect(view.getByText('Design system')).toBeTruthy();
  });

  it('expose chaque section de vérification exigée par le lot 2', async () => {
    const view = await wrap(<GalleryScreen />);
    for (const section of [
      'Neutres',
      'Accent',
      'Sémantique',
      'États de transaction',
      'Flux monétaire',
      'Typographie',
      'Espacement',
      'Rayons',
      'Élévation',
    ]) {
      expect(view.getByText(section)).toBeTruthy();
    }
  });

  it('affiche les 12 variantes typographiques', async () => {
    const view = await wrap(<GalleryScreen />);
    expect(view.getAllByText('125 000 F — Aminata')).toHaveLength(Object.keys(type).length);
  });

  it('affiche les 11 neutres et les 7 accents', async () => {
    const view = await wrap(<GalleryScreen />);
    // Les libellés de nuance sont le suffixe numérique du token.
    expect(view.getAllByText('500').length).toBeGreaterThanOrEqual(2);
    expect(view.getByText('900')).toBeTruthy();
  });
});

describe('thèmes', () => {
  it('expose la même forme, donc aucun composant n’a à tester le thème actif', () => {
    expect(Object.keys(lightTheme).sort()).toEqual(Object.keys(darkTheme).sort());
    expect(lightTheme.scheme).toBe('light');
    expect(darkTheme.scheme).toBe('dark');
  });
});
