import { act, renderHook } from '@testing-library/react-native';

import { useDelayedLoading } from '@/components/feedback';
import { SKELETON_DELAY_MS } from '@/theme';

/**
 * Isolé dans son propre fichier : ces tests manipulent les faux timers de Jest.
 * Les laisser cohabiter avec des rendus de composants pollue ces derniers — le
 * rendu asynchrone de RNTL 14 reste alors bloqué sur des microtâches, et les
 * suites voisines échouent de façon parfaitement opaque.
 */

beforeEach(() => jest.useFakeTimers());
afterEach(() => jest.useRealTimers());

type Props = { loading: boolean };

const setup = (loading: boolean) =>
  renderHook((props: Props) => useDelayedLoading(props.loading), {
    initialProps: { loading },
  });

/**
 * `act` **asynchrone** : avancer les faux timers de façon synchrone laisse le
 * rendu concurrent de React 19 en suspens, et `result.current` reste `null`.
 *
 * Même raison pour `await rerender(...)` : dans RNTL 14, `render`, `renderHook`
 * ET `rerender` sont tous asynchrones.
 */
const advance = (ms: number) =>
  act(async () => {
    jest.advanceTimersByTime(ms);
  });

describe('useDelayedLoading — NFR-07', () => {
  it('n’affiche rien sur une réponse plus rapide que le seuil', async () => {
    const { result, rerender } = await setup(true);

    await advance(SKELETON_DELAY_MS - 50);
    expect(result.current).toBe(false);

    // La réponse arrive avant le seuil : aucun squelette n'aura clignoté.
    await rerender({ loading: false });
    expect(result.current).toBe(false);
  });

  it('affiche le squelette au-delà du seuil', async () => {
    const { result } = await setup(true);

    await advance(SKELETON_DELAY_MS + 10);
    expect(result.current).toBe(true);
  });

  it('se rétracte immédiatement à la fin du chargement', async () => {
    const { result, rerender } = await setup(true);

    await advance(SKELETON_DELAY_MS + 10);
    expect(result.current).toBe(true);

    await rerender({ loading: false });
    expect(result.current).toBe(false);
  });

  it('ne s’affiche jamais quand le chargement n’a pas commencé', async () => {
    const { result } = await setup(false);

    await advance(SKELETON_DELAY_MS * 5);
    expect(result.current).toBe(false);
  });
});
