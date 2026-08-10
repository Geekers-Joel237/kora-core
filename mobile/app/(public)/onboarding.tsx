import { useState } from 'react';
import { StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  interpolate,
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';
import { router } from 'expo-router';

import { Button } from '@/components/action';
import { Icon, Spacer, Text, type IconName } from '@/components/primitives';
import { KvKey, kvSetBoolean } from '@/lib/storage/kv';
import { radius, space, spring, useTheme } from '@/theme';

/** Les textes vivent dans `src/i18n/` ; seule la structure reste ici. */
const SLIDES: { key: 'slide1' | 'slide2' | 'slide3'; icon: IconName }[] = [
  { key: 'slide1', icon: 'shield' },
  { key: 'slide2', icon: 'activity' },
  { key: 'slide3', icon: 'lock' },
];

/** L'illustration se déplace à 0,4× la vitesse du texte. §2.1 */
const PARALLAX_RATIO = 0.4;
const DOT_WIDTH = 8;
const DOT_ACTIVE_WIDTH = 24;
const SWIPE_THRESHOLD_RATIO = 0.25;
const ICON_BOX = 140;

export default function OnboardingScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const [index, setIndex] = useState(0);

  const offset = useSharedValue(0);

  const goTo = (next: number) => {
    const clamped = Math.max(0, Math.min(SLIDES.length - 1, next));
    setIndex(clamped);
    offset.value = withSpring(-clamped * width, spring.standard);
  };

  const finish = () => {
    kvSetBoolean(KvKey.onboardingSeen, true);
    router.replace('/login');
  };

  const pan = Gesture.Pan()
    .onChange((event) => {
      'worklet';
      offset.value += event.changeX;
    })
    .onEnd((event) => {
      'worklet';
      const moved = -offset.value / width;
      const shouldAdvance = event.translationX < -width * SWIPE_THRESHOLD_RATIO;
      const shouldGoBack = event.translationX > width * SWIPE_THRESHOLD_RATIO;
      const next = shouldAdvance
        ? Math.ceil(moved)
        : shouldGoBack
          ? Math.floor(moved)
          : Math.round(moved);
      runOnJS(goTo)(next);
    });

  const trackStyle = useAnimatedStyle(() => ({ transform: [{ translateX: offset.value }] }));

  const parallaxStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: offset.value * -(1 - PARALLAX_RATIO) }],
  }));

  const isLast = index === SLIDES.length - 1;

  return (
    <View style={[styles.container, { backgroundColor: theme.bg.app }]}>
      <View style={[styles.skip, { top: insets.top + space[2] }]}>
        {!isLast && (
          <Button
            label={t('common.skip')}
            onPress={finish}
            variant="ghost"
            size="sm"
            fullWidth={false}
          />
        )}
      </View>

      <GestureDetector gesture={pan}>
        <View style={styles.stage}>
          <Animated.View style={[styles.track, { width: width * SLIDES.length }, trackStyle]}>
            {SLIDES.map((slide) => (
              <View key={slide.key} style={[styles.slide, { width }]}>
                <Animated.View
                  style={[
                    styles.iconBox,
                    { backgroundColor: theme.accent.wash, borderRadius: radius['2xl'] },
                    parallaxStyle,
                  ]}
                >
                  <Icon name={slide.icon} size="xl" color={theme.accent.primary} />
                </Animated.View>

                <Spacer size={10} />
                <Text variant="titleLg" align="center">
                  {t(`onboarding.${slide.key}.title`)}
                </Text>
                <Spacer size={3} />
                <Text variant="bodyLg" color="secondary" align="center">
                  {t(`onboarding.${slide.key}.body`)}
                </Text>
              </View>
            ))}
          </Animated.View>
        </View>
      </GestureDetector>

      <View style={[styles.footer, { paddingBottom: insets.bottom + space[6] }]}>
        <View style={styles.dots}>
          {SLIDES.map((slide, dotIndex) => (
            <Dot key={slide.key} active={dotIndex === index} color={theme.accent.primary} />
          ))}
        </View>

        <Spacer size={6} />

        <Button
          label={isLast ? t('common.start') : t('common.next')}
          onPress={() => (isLast ? finish() : goTo(index + 1))}
        />
      </View>
    </View>
  );
}

/** Le point actif s'allonge de 8 à 24 dp. §2.1 */
function Dot({ active, color }: { active: boolean; color: string }) {
  const theme = useTheme();
  const progress = useSharedValue(active ? 1 : 0);
  progress.value = withSpring(active ? 1 : 0, spring.snappy);

  const animatedStyle = useAnimatedStyle(() => ({
    width: interpolate(progress.value, [0, 1], [DOT_WIDTH, DOT_ACTIVE_WIDTH]),
  }));

  return (
    <Animated.View
      style={[
        styles.dot,
        { backgroundColor: active ? color : theme.text.disabled },
        animatedStyle,
      ]}
    />
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  skip: { position: 'absolute', right: space[4], zIndex: 1 },
  stage: { flex: 1, overflow: 'hidden', justifyContent: 'center' },
  track: { flexDirection: 'row', flex: 1 },
  slide: { alignItems: 'center', justifyContent: 'center', paddingHorizontal: space[8] },
  iconBox: {
    width: ICON_BOX,
    height: ICON_BOX,
    alignItems: 'center',
    justifyContent: 'center',
  },
  footer: { paddingHorizontal: space[5] },
  dots: { flexDirection: 'row', justifyContent: 'center', gap: space[2] },
  dot: { height: DOT_WIDTH, borderRadius: radius.full },
});
