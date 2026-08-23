import * as Haptics from 'expo-haptics';

import {
  __resetHaptics,
  areHapticsEnabled,
  haptic,
  setHapticsEnabled,
  triggerHaptic,
} from '../haptics';

jest.mock('expo-haptics', () => ({
  impactAsync: jest.fn(async () => undefined),
  selectionAsync: jest.fn(async () => undefined),
  notificationAsync: jest.fn(async () => undefined),
  ImpactFeedbackStyle: { Light: 'light', Medium: 'medium', Heavy: 'heavy' },
  NotificationFeedbackType: { Success: 'success', Warning: 'warning', Error: 'error' },
}));

const impact = Haptics.impactAsync as jest.Mock;
const selection = Haptics.selectionAsync as jest.Mock;
const notification = Haptics.notificationAsync as jest.Mock;

beforeEach(() => {
  jest.clearAllMocks();
  __resetHaptics();
  jest.useFakeTimers();
  jest.setSystemTime(0);
});

afterEach(() => jest.useRealTimers());

describe('correspondances — §3', () => {
  it('mappe chaque impulsion sur le bon effet système', () => {
    haptic.tap();
    expect(impact).toHaveBeenCalledWith('light');

    jest.advanceTimersByTime(100);
    haptic.press();
    expect(impact).toHaveBeenCalledWith('medium');

    jest.advanceTimersByTime(100);
    haptic.commit();
    expect(impact).toHaveBeenCalledWith('heavy');

    jest.advanceTimersByTime(100);
    haptic.select();
    expect(selection).toHaveBeenCalledTimes(1);

    jest.advanceTimersByTime(100);
    haptic.success();
    expect(notification).toHaveBeenCalledWith('success');

    jest.advanceTimersByTime(100);
    haptic.warning();
    expect(notification).toHaveBeenCalledWith('warning');

    jest.advanceTimersByTime(100);
    haptic.error();
    expect(notification).toHaveBeenCalledWith('error');
  });

  it('traite `none` comme un no-op explicite', () => {
    triggerHaptic('none');
    expect(impact).not.toHaveBeenCalled();
    expect(selection).not.toHaveBeenCalled();
    expect(notification).not.toHaveBeenCalled();
  });
});

describe('étranglement à 50 ms — §3', () => {
  it('ignore une seconde impulsion émise trop tôt', () => {
    haptic.tap();
    jest.advanceTimersByTime(20);
    haptic.tap();
    jest.advanceTimersByTime(20);
    haptic.press();

    // Deux impulsions à moins de 50 ms se ressentent comme une vibration
    // parasite : une seule doit passer.
    expect(impact).toHaveBeenCalledTimes(1);
  });

  it('laisse passer une impulsion au-delà du seuil', () => {
    haptic.tap();
    jest.advanceTimersByTime(60);
    haptic.tap();
    expect(impact).toHaveBeenCalledTimes(2);
  });

  it('étrangle globalement, toutes familles confondues', () => {
    haptic.tap();
    jest.advanceTimersByTime(10);
    haptic.success();
    expect(notification).not.toHaveBeenCalled();
  });
});

describe('préférence utilisateur', () => {
  it('est active par défaut', () => {
    expect(areHapticsEnabled()).toBe(true);
  });

  it('coupe intégralement le canal quand elle est désactivée', () => {
    setHapticsEnabled(false);
    haptic.tap();
    jest.advanceTimersByTime(100);
    haptic.commit();
    jest.advanceTimersByTime(100);
    haptic.error();

    expect(impact).not.toHaveBeenCalled();
    expect(notification).not.toHaveBeenCalled();
  });

  it('se réactive et persiste', () => {
    setHapticsEnabled(false);
    setHapticsEnabled(true);
    haptic.tap();
    expect(impact).toHaveBeenCalledTimes(1);
  });
});

describe('robustesse', () => {
  it('n’interrompt jamais l’interaction si le moteur haptique échoue', () => {
    impact.mockRejectedValueOnce(new Error('pas de moteur haptique'));
    expect(() => haptic.tap()).not.toThrow();
  });
});
