import { useEffect } from 'react';
import Svg, { Circle, Path } from 'react-native-svg';
import Animated, {
  useAnimatedProps,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';

import { ease } from '@/theme/easing';
import { timing, useReduceMotion, useTheme } from '@/theme';

const AnimatedCircle = Animated.createAnimatedComponent(Circle);
const AnimatedPath = Animated.createAnimatedComponent(Path);

const SIZE = 96;
const RADIUS = 44;
const STROKE = 4;
const RING_LENGTH = 2 * Math.PI * RADIUS;
/** Longueur approximative du tracé de la coche, mesurée sur le chemin. */
const CHECK_LENGTH = 60;

/** Instants de la chorégraphie — `docs/03-motion-and-feel.md` §6.4. */
export const SUCCESS_TIMELINE = {
  ring: 120,
  check: 280,
  amount: 380,
  counterpart: 460,
  balance: 520,
  actions: 650,
} as const;

/**
 * Coche de succès — §6.4.
 *
 * L'anneau puis le tracé se **dessinent** par `strokeDashoffset` animé, sur le
 * thread UI. Ce qui est interdit ici : confettis, particules, Lottie, mise à
 * l'échelle exagérée. Un paiement réussi est un fait sobre — la qualité vient
 * de la précision du minutage, pas de l'exubérance.
 */
export function SuccessCheck({ failed = false }: { failed?: boolean }) {
  const theme = useTheme();
  const reduceMotion = useReduceMotion();

  const ring = useSharedValue(reduceMotion ? 0 : RING_LENGTH);
  const check = useSharedValue(reduceMotion ? 0 : CHECK_LENGTH);

  const color = failed ? theme.status.failed.fg : theme.accent.primary;

  useEffect(() => {
    if (reduceMotion) return;

    ring.value = withDelay(
      SUCCESS_TIMELINE.ring,
      withTiming(0, { duration: timing.slow, easing: ease }),
    );
    check.value = withDelay(
      SUCCESS_TIMELINE.check,
      withTiming(0, { duration: timing.normal, easing: ease }),
    );
  }, [reduceMotion, ring, check]);

  const ringProps = useAnimatedProps(() => ({ strokeDashoffset: ring.value }));
  const checkProps = useAnimatedProps(() => ({ strokeDashoffset: check.value }));

  return (
    <Svg width={SIZE} height={SIZE} viewBox="0 0 96 96" fill="none">
      <AnimatedCircle
        cx={48}
        cy={48}
        r={RADIUS}
        stroke={color}
        strokeWidth={STROKE}
        strokeLinecap="round"
        strokeDasharray={RING_LENGTH}
        animatedProps={ringProps}
        // Le tracé part du haut plutôt que de la droite : un anneau qui se
        // ferme depuis midi se lit comme une progression, pas comme un tour.
        transform="rotate(-90 48 48)"
      />
      <AnimatedPath
        d={failed ? 'M34 34 L62 62 M62 34 L34 62' : 'M32 49 L43 60 L64 37'}
        stroke={color}
        strokeWidth={STROKE}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeDasharray={CHECK_LENGTH}
        animatedProps={checkProps}
      />
    </Svg>
  );
}