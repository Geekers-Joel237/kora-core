import { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';
import { router } from 'expo-router';

import { Button, IconButton } from '@/components/action';
import { PinPad, TextField } from '@/components/input';
import { Spacer, Text } from '@/components/primitives';
import { register } from '@/features/auth/api';
import { isEmailConflict, registerErrorMessage } from '@/features/auth/messages';
import { beginOtpFlow } from '@/features/auth/otpFlow';
import { useSession } from '@/features/auth/session';
import { radius, space, spring, stroke, useTheme } from '@/theme';

/**
 * Inscription en 4 étapes — `docs/05-screens.md` §2.3.
 *
 * Une question par écran. Le taux d'abandon d'un formulaire mobile à cinq
 * champs est double de celui d'un parcours en quatre étapes.
 */
type Step = 'name' | 'email' | 'phone' | 'pin';

const STEPS: Step[] = ['name', 'email', 'phone', 'pin'];

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PREFIX_PATTERN = /^\+\d{1,4}$/;
const NUMBER_PATTERN = /^\d{8,15}$/;
const PIN_MIN = 4;
const PIN_MAX = 8;
const PROGRESS_HEIGHT = 3;

export default function RegisterScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const rememberProfile = useSession((state) => state.rememberProfile);

  const [step, setStep] = useState<Step>('name');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phonePrefix, setPhonePrefix] = useState('+225');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [firstPin, setFirstPin] = useState<string | null>(null);

  const [fieldError, setFieldError] = useState<string | null>(null);
  const [pinError, setPinError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const index = STEPS.indexOf(step);
  const progress = useSharedValue((index + 1) / STEPS.length);
  progress.value = withSpring((index + 1) / STEPS.length, spring.standard);

  const progressStyle = useAnimatedStyle(() => ({
    width: `${progress.value * 100}%`,
  }));

  const goBack = () => {
    setFieldError(null);
    setPinError(null);
    if (index === 0) {
      router.back();
      return;
    }
    if (step === 'pin') setFirstPin(null);
    setStep(STEPS[index - 1]!);
  };

  const advance = (from: Step, valid: boolean, message: string) => {
    if (!valid) {
      setFieldError(message);
      return;
    }
    setFieldError(null);
    setStep(STEPS[STEPS.indexOf(from) + 1]!);
  };

  const submit = useCallback(
    async (rawPin: string) => {
      setLoading(true);
      setPinError(null);
      try {
        await register({
          fullName: fullName.trim(),
          email: email.trim(),
          phonePrefix,
          phoneNumber,
          rawPin,
        });

        // Contrat §6.3 — aucun endpoint de profil : nom et téléphone ne sont
        // connus qu'ici. Les perdre signifierait afficher l'e-mail à la place.
        rememberProfile({
          fullName: fullName.trim(),
          phone: `${phonePrefix}${phoneNumber}`,
          email: email.trim(),
        });

        beginOtpFlow({ origin: 'register', email: email.trim(), rawPin });
        router.push('/verify-otp');
      } catch (error) {
        // `409` sur l'e-mail : retour direct à l'étape concernée, pas un
        // message générique sur l'écran du PIN. Le test porte sur le statut,
        // jamais sur le texte rendu — celui-ci change avec la langue.
        if (isEmailConflict(error)) {
          setFirstPin(null);
          setStep('email');
          setFieldError(registerErrorMessage(error));
        } else {
          setFirstPin(null);
          setPinError(registerErrorMessage(error));
        }
      } finally {
        setLoading(false);
      }
    },
    [fullName, email, phonePrefix, phoneNumber, rememberProfile],
  );

  const handlePin = useCallback(
    (pin: string) => {
      if (pin.length < PIN_MIN || pin.length > PIN_MAX) {
        setPinError(t('register.pinLength', { min: PIN_MIN, max: PIN_MAX }));
        return;
      }
      if (firstPin === null) {
        setFirstPin(pin);
        setPinError(null);
        return;
      }
      if (pin !== firstPin) {
        setFirstPin(null);
        setPinError(t('register.pinMismatch'));
        return;
      }
      void submit(pin);
    },
    [firstPin, submit, t],
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.bg.app }]}>
      <View style={[styles.nav, { paddingTop: insets.top + space[2] }]}>
        <IconButton
          name="back"
          onPress={goBack}
          accessibilityLabel={t('common.previousStep')}
          testID="register-back"
        />
      </View>

      <View style={[styles.progressTrack, { backgroundColor: theme.overlay.hairline }]}>
        <Animated.View
          style={[
            styles.progressFill,
            { backgroundColor: theme.accent.primary, borderRadius: radius.full },
            progressStyle,
          ]}
        />
      </View>

      {step === 'pin' ? (
        <PinPad
          key={firstPin === null ? 'first' : 'confirm'}
          title={firstPin === null ? t('register.pinChoose') : t('register.pinConfirm')}
          subtitle={
            firstPin === null
              ? t('register.pinHint', { min: PIN_MIN, max: PIN_MAX })
              : t('register.pinConfirmHint')
          }
          onComplete={handlePin}
          error={pinError}
          loading={loading}
        />
      ) : (
        <View style={styles.form}>
          {step === 'name' && (
            <>
              <Text variant="titleLg">{t('register.nameTitle')}</Text>
              <Spacer size={8} />
              <TextField
                label={t('register.nameLabel')}
                value={fullName}
                onChangeText={setFullName}
                placeholder={t('register.namePlaceholder')}
                autoCapitalize="words"
                autoComplete="name"
                error={fieldError}
                onSubmitEditing={() =>
                  advance('name', fullName.trim().length > 0, t('register.nameRequired'))
                }
                autoFocus
                testID="register-name"
              />
              <Spacer size={6} />
              <Button
                label={t('common.continue')}
                onPress={() =>
                  advance('name', fullName.trim().length > 0, t('register.nameRequired'))
                }
              />
            </>
          )}

          {step === 'email' && (
            <>
              <Text variant="titleLg">{t('register.emailTitle')}</Text>
              <Spacer size={2} />
              <Text variant="bodyMd" color="secondary">
                {t('register.emailSubtitle')}
              </Text>
              <Spacer size={8} />
              <TextField
                label={t('login.emailLabel')}
                value={email}
                onChangeText={setEmail}
                placeholder={t('login.emailPlaceholder')}
                keyboardType="email-address"
                autoComplete="email"
                error={fieldError}
                onSubmitEditing={() =>
                  advance('email', EMAIL_PATTERN.test(email.trim()), t('register.emailInvalid'))
                }
                autoFocus
                testID="register-email"
              />
              <Spacer size={6} />
              <Button
                label={t('common.continue')}
                onPress={() =>
                  advance('email', EMAIL_PATTERN.test(email.trim()), t('register.emailInvalid'))
                }
              />
            </>
          )}

          {step === 'phone' && (
            <>
              <Text variant="titleLg">{t('register.phoneTitle')}</Text>
              <Spacer size={2} />
              <Text variant="bodyMd" color="secondary">
                {t('register.phoneSubtitle')}
              </Text>
              <Spacer size={8} />
              <View style={styles.phoneRow}>
                <View style={styles.prefix}>
                  <TextField
                    label={t('phone.prefixLabel')}
                    value={phonePrefix}
                    onChangeText={setPhonePrefix}
                    keyboardType="phone-pad"
                    testID="register-prefix"
                  />
                </View>
                <View style={styles.number}>
                  <TextField
                    label={t('phone.numberLabel')}
                    value={phoneNumber}
                    onChangeText={(text) => setPhoneNumber(text.replace(/\D/g, ''))}
                    placeholder={t('phone.numberPlaceholder')}
                    keyboardType="phone-pad"
                    autoComplete="tel"
                    error={fieldError}
                    autoFocus
                    testID="register-phone"
                  />
                </View>
              </View>
              <Spacer size={6} />
              <Button
                label={t('common.continue')}
                onPress={() =>
                  advance(
                    'phone',
                    PREFIX_PATTERN.test(phonePrefix) && NUMBER_PATTERN.test(phoneNumber),
                    t('register.phoneInvalid'),
                  )
                }
              />
            </>
          )}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  nav: { height: space[16], justifyContent: 'center', paddingHorizontal: space[2] },
  progressTrack: { height: PROGRESS_HEIGHT, marginHorizontal: space[5] },
  progressFill: { height: PROGRESS_HEIGHT, borderWidth: stroke.hairline, borderColor: 'transparent' },
  form: { flex: 1, paddingHorizontal: space[5], paddingTop: space[8] },
  phoneRow: { flexDirection: 'row', gap: space[3] },
  prefix: { width: space[16] * 1.6 },
  number: { flex: 1 },
});
