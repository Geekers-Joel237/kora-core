import { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { router } from 'expo-router';

import { Button, IconButton } from '@/components/action';
import { PinPad, TextField } from '@/components/input';
import { Spacer, Text } from '@/components/primitives';
import { login } from '@/features/auth/api';
import { loginErrorMessage } from '@/features/auth/messages';
import { beginOtpFlow } from '@/features/auth/otpFlow';
import { KvKey, kvGetString } from '@/lib/storage/kv';
import { space, useTheme } from '@/theme';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function LoginScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const [step, setStep] = useState<'email' | 'pin'>('email');
  const [email, setEmail] = useState(kvGetString(KvKey.lastEmail) ?? '');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [pinError, setPinError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submitEmail = useCallback(() => {
    if (!EMAIL_PATTERN.test(email.trim())) {
      setEmailError('Entrez une adresse e-mail valide.');
      return;
    }
    setEmailError(null);
    setStep('pin');
  }, [email]);

  const submitPin = useCallback(
    async (rawPin: string) => {
      setLoading(true);
      setPinError(null);
      try {
        await login(email.trim(), rawPin);
        // Le PIN est conservé en mémoire volatile pour le seul renvoi d'OTP.
        beginOtpFlow({ origin: 'login', email: email.trim(), rawPin });
        router.push('/verify-otp');
      } catch (error) {
        // ⚠️ 401 et 404 produisent le MÊME message — voir messages.ts.
        setPinError(loginErrorMessage(error));
      } finally {
        setLoading(false);
      }
    },
    [email],
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.bg.app }]}>
      <View style={[styles.nav, { paddingTop: insets.top + space[2] }]}>
        {step === 'pin' && (
          <IconButton
            name="back"
            onPress={() => setStep('email')}
            accessibilityLabel="Revenir à l'e-mail"
            testID="back-to-email"
          />
        )}
      </View>

      {step === 'email' ? (
        <View style={styles.form}>
          <Text variant="titleLg">Bon retour</Text>
          <Spacer size={2} />
          <Text variant="bodyMd" color="secondary">
            Connectez-vous pour accéder à votre portefeuille.
          </Text>

          <Spacer size={8} />

          <TextField
            label="Adresse e-mail"
            value={email}
            onChangeText={setEmail}
            placeholder="vous@exemple.com"
            keyboardType="email-address"
            autoComplete="email"
            error={emailError}
            onSubmitEditing={submitEmail}
            autoFocus
            testID="login-email"
          />

          <Spacer size={6} />
          <Button label="Continuer" onPress={submitEmail} />

          <Spacer size={4} />
          <Button
            label="Créer un compte"
            onPress={() => router.push('/register')}
            variant="ghost"
            size="md"
          />
        </View>
      ) : (
        <PinPad
          title="Entrez votre PIN"
          subtitle={email.trim()}
          onComplete={(pin) => void submitPin(pin)}
          error={pinError}
          loading={loading}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  nav: { height: space[16], justifyContent: 'center', paddingHorizontal: space[2] },
  form: { flex: 1, paddingHorizontal: space[5] },
});
