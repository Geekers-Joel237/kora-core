import { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { router } from 'expo-router';

import { Button, IconButton } from '@/components/action';
import { PinPad, TextField } from '@/components/input';
import { Spacer, Text } from '@/components/primitives';
import { register } from '@/features/auth/api';
import { registerErrorMessage, violationField } from '@/features/auth/messages';
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
        // message générique sur l'écran du PIN.
        if (violationField(error) === 'email' || registerErrorMessage(error).includes('e-mail')) {
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
        setPinError(`Le PIN doit contenir entre ${PIN_MIN} et ${PIN_MAX} chiffres.`);
        return;
      }
      if (firstPin === null) {
        setFirstPin(pin);
        setPinError(null);
        return;
      }
      if (pin !== firstPin) {
        setFirstPin(null);
        setPinError('Les deux PIN ne correspondent pas.');
        return;
      }
      void submit(pin);
    },
    [firstPin, submit],
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.bg.app }]}>
      <View style={[styles.nav, { paddingTop: insets.top + space[2] }]}>
        <IconButton name="back" onPress={goBack} accessibilityLabel="Étape précédente" testID="register-back" />
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
          title={firstPin === null ? 'Choisissez un PIN' : 'Confirmez votre PIN'}
          subtitle={
            firstPin === null
              ? `Entre ${PIN_MIN} et ${PIN_MAX} chiffres`
              : 'Saisissez-le une seconde fois'
          }
          onComplete={handlePin}
          error={pinError}
          loading={loading}
        />
      ) : (
        <View style={styles.form}>
          {step === 'name' && (
            <>
              <Text variant="titleLg">Comment vous appelez-vous ?</Text>
              <Spacer size={8} />
              <TextField
                label="Nom complet"
                value={fullName}
                onChangeText={setFullName}
                placeholder="Aminata Diallo"
                autoCapitalize="words"
                autoComplete="name"
                error={fieldError}
                onSubmitEditing={() =>
                  advance('name', fullName.trim().length > 0, 'Entrez votre nom complet.')
                }
                autoFocus
                testID="register-name"
              />
              <Spacer size={6} />
              <Button
                label="Continuer"
                onPress={() =>
                  advance('name', fullName.trim().length > 0, 'Entrez votre nom complet.')
                }
              />
            </>
          )}

          {step === 'email' && (
            <>
              <Text variant="titleLg">Votre adresse e-mail</Text>
              <Spacer size={2} />
              <Text variant="bodyMd" color="secondary">
                Vos codes de vérification y seront envoyés.
              </Text>
              <Spacer size={8} />
              <TextField
                label="Adresse e-mail"
                value={email}
                onChangeText={setEmail}
                placeholder="vous@exemple.com"
                keyboardType="email-address"
                autoComplete="email"
                error={fieldError}
                onSubmitEditing={() =>
                  advance('email', EMAIL_PATTERN.test(email.trim()), 'Entrez une adresse valide.')
                }
                autoFocus
                testID="register-email"
              />
              <Spacer size={6} />
              <Button
                label="Continuer"
                onPress={() =>
                  advance('email', EMAIL_PATTERN.test(email.trim()), 'Entrez une adresse valide.')
                }
              />
            </>
          )}

          {step === 'phone' && (
            <>
              <Text variant="titleLg">Votre numéro de téléphone</Text>
              <Spacer size={2} />
              <Text variant="bodyMd" color="secondary">
                {"Il servira à recevoir de l'argent d'autres utilisateurs Kora."}
              </Text>
              <Spacer size={8} />
              <View style={styles.phoneRow}>
                <View style={styles.prefix}>
                  <TextField
                    label="Indicatif"
                    value={phonePrefix}
                    onChangeText={setPhonePrefix}
                    keyboardType="phone-pad"
                    testID="register-prefix"
                  />
                </View>
                <View style={styles.number}>
                  <TextField
                    label="Numéro"
                    value={phoneNumber}
                    onChangeText={(text) => setPhoneNumber(text.replace(/\D/g, ''))}
                    placeholder="0708091011"
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
                label="Continuer"
                onPress={() =>
                  advance(
                    'phone',
                    PREFIX_PATTERN.test(phonePrefix) && NUMBER_PATTERN.test(phoneNumber),
                    'Indicatif ou numéro invalide.',
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
