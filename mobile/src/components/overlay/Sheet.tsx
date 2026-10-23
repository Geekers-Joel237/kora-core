import { useCallback, useEffect } from 'react';
import { BackHandler, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Text } from '@/components/primitives';
import { haptic } from '@/lib/haptics';
import { radius, space, spring, timing, useReduceMotion, useTheme } from '@/theme';

export interface SheetProps {
  visible: boolean;
  onClose: () => void;
  title?: string;
  /** Fraction de la hauteur d'écran occupée. §5.2 */
  snapRatio?: number;
  children: React.ReactNode;
}

/** Fermeture si le déplacement dépasse 25 % de la hauteur… */
const DISMISS_DISTANCE_RATIO = 0.25;
/** …ou si la vélocité descendante dépasse 500 dp/s. §5.2 */
const DISMISS_VELOCITY = 500;
/** Résistance au-delà du point haut : la feuille ralentit, elle ne bloque pas net. */
const OVERDRAG_RESISTANCE = 0.35;
const SCRIM_OPACITY = 0.72;

/**
 * Feuille modale — `docs/03-motion-and-feel.md` §5.2.
 *
 * Le glissement suit le doigt en `gesture` (aucun rebond toléré sur un geste),
 * avec **résistance progressive au-delà du point haut** : tirer plus haut que
 * la butée ralentit le mouvement au lieu de le bloquer net. C'est ce détail
 * qui distingue une feuille qui a de la matière d'une feuille qui bute.
 */
export function Sheet({ visible, onClose, title, snapRatio = 0.5, children }: SheetProps) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const { height: screenHeight } = useWindowDimensions();
  const reduceMotion = useReduceMotion();

  const sheetHeight = screenHeight * snapRatio;
  const translateY = useSharedValue(sheetHeight);
  const scrim = useSharedValue(0);

  const close = useCallback(() => onClose(), [onClose]);

  useEffect(() => {
    if (visible) {
      haptic.press();
      translateY.value = reduceMotion
        ? withTiming(0, { duration: timing.fast })
        : withSpring(0, spring.gentle);
      scrim.value = withTiming(SCRIM_OPACITY, { duration: timing.normal });
    } else {
      translateY.value = reduceMotion
        ? withTiming(sheetHeight, { duration: timing.fast })
        : withSpring(sheetHeight, spring.gentle);
      scrim.value = withTiming(0, { duration: timing.fast });
    }
  }, [visible, reduceMotion, sheetHeight, translateY, scrim]);

  // Le bouton retour matériel ferme la feuille au lieu de quitter l'écran.
  useEffect(() => {
    if (!visible) return;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      close();
      return true;
    });
    return () => subscription.remove();
  }, [visible, close]);

  const pan = Gesture.Pan()
    .onChange((event) => {
      'worklet';
      const next = translateY.value + event.changeY;
      // Au-dessus de la butée, le déplacement est amorti — la feuille résiste
      // sans se bloquer, ce qui donne la sensation d'élastique.
      translateY.value = next < 0 ? next * OVERDRAG_RESISTANCE : next;
    })
    .onEnd((event) => {
      'worklet';
      const shouldDismiss =
        translateY.value > sheetHeight * DISMISS_DISTANCE_RATIO ||
        event.velocityY > DISMISS_VELOCITY;

      if (shouldDismiss) {
        translateY.value = withSpring(sheetHeight, spring.gesture);
        runOnJS(close)();
      } else {
        translateY.value = withSpring(0, spring.gesture);
      }
    });

  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
  }));

  const scrimStyle = useAnimatedStyle(() => ({ opacity: scrim.value }));

  if (!visible) return null;

  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      <Animated.View
        style={[StyleSheet.absoluteFill, { backgroundColor: theme.overlay.scrim }, scrimStyle]}
      >
        {/* Le voile ferme la feuille — sans haptique : la fermeture par geste
            n'en produit pas non plus (§3). */}
        <Animated.View style={StyleSheet.absoluteFill} onTouchEnd={close} />
      </Animated.View>

      <GestureDetector gesture={pan}>
        <Animated.View
          style={[
            styles.sheet,
            {
              height: sheetHeight,
              backgroundColor: theme.bg.surface1,
              borderTopLeftRadius: radius.xl,
              borderTopRightRadius: radius.xl,
              paddingBottom: insets.bottom + space[4],
            },
            sheetStyle,
          ]}
        >
          <View style={[styles.grabber, { backgroundColor: theme.overlay.borderStrong }]} />
          {title && (
            <View style={styles.header}>
              <Text variant="titleMd">{title}</Text>
            </View>
          )}
          <View style={styles.body}>{children}</View>
        </Animated.View>
      </GestureDetector>
    </View>
  );
}

const GRABBER_WIDTH = 36;
const GRABBER_HEIGHT = 4;

const styles = StyleSheet.create({
  sheet: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  grabber: {
    width: GRABBER_WIDTH,
    height: GRABBER_HEIGHT,
    borderRadius: radius.full,
    alignSelf: 'center',
    marginTop: space[2],
  },
  header: { paddingHorizontal: space[5], paddingTop: space[4] },
  body: { flex: 1, paddingHorizontal: space[5], paddingTop: space[4] },
});
