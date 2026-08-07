import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon, Text, type IconName } from '@/components/primitives';
import { radius, space, spring, stroke, timing, useReduceMotion, useTheme } from '@/theme';

export type ToastTone = 'neutral' | 'success' | 'failed';

interface ToastPayload {
  message: string;
  tone?: ToastTone;
  icon?: IconName;
}

interface ToastContextValue {
  show: (payload: ToastPayload) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const VISIBLE_MS = 3000;
const ENTER_FROM = -80;
const SWIPE_DISMISS_DY = -20;

/**
 * Notification non bloquante — `docs/04-components.md` §7.
 *
 * Réservée aux confirmations légères : copie effectuée, réglage enregistré.
 * **Un `Toast` n'annonce jamais le résultat d'une opération monétaire** : ce
 * résultat mérite toujours un écran plein.
 */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [payload, setPayload] = useState<ToastPayload | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const show = useCallback((next: ToastPayload) => {
    if (timer.current) clearTimeout(timer.current);
    setPayload(next);
    timer.current = setTimeout(() => setPayload(null), VISIBLE_MS);
  }, []);

  useEffect(() => () => (timer.current ? clearTimeout(timer.current) : undefined), []);

  const dismiss = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    setPayload(null);
  }, []);

  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      {payload && <ToastView payload={payload} onDismiss={dismiss} />}
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast doit être utilisé à l’intérieur de <ToastProvider>');
  return context;
}

function ToastView({ payload, onDismiss }: { payload: ToastPayload; onDismiss: () => void }) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReduceMotion();
  const translateY = useSharedValue(ENTER_FROM);
  const opacity = useSharedValue(0);

  useEffect(() => {
    opacity.value = withTiming(1, { duration: timing.instant });
    translateY.value = reduceMotion
      ? withTiming(0, { duration: timing.instant })
      : withSpring(0, spring.bouncy);
  }, [translateY, opacity, reduceMotion]);

  // Balayage vers le haut pour écarter. Aucune haptique : l'utilisateur n'a
  // rien confirmé, il a seulement fait disparaître une information.
  const swipe = Gesture.Pan().onEnd((event) => {
    'worklet';
    if (event.translationY < SWIPE_DISMISS_DY) runOnJS(onDismiss)();
  });

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ translateY: translateY.value }],
  }));

  const tint =
    payload.tone === 'success'
      ? theme.status.success.fg
      : payload.tone === 'failed'
        ? theme.status.failed.fg
        : theme.text.primary;

  return (
    <GestureDetector gesture={swipe}>
      <Animated.View
        style={[
          styles.container,
          { top: insets.top + space[2] },
          animatedStyle,
        ]}
        pointerEvents="box-none"
      >
        <View
          style={[
            styles.card,
            {
              backgroundColor: theme.bg.surface3,
              borderRadius: radius.md,
              borderWidth: stroke.hairline,
              borderColor: theme.overlay.border,
            },
            theme.floatingShadow,
          ]}
        >
          {payload.icon && <Icon name={payload.icon} size="sm" color={tint} />}
          <Text variant="bodyMd" tint={tint} numberOfLines={2}>
            {payload.message}
          </Text>
        </View>
      </Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  container: { position: 'absolute', left: space[4], right: space[4] },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[2],
    paddingHorizontal: space[4],
    paddingVertical: space[3],
  },
});
