import { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import * as ScreenCapture from 'expo-screen-capture';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';
import { router } from 'expo-router';

import { Button, IconButton } from '@/components/action';
import { OtpInput } from '@/components/input';
import { Spacer, Text } from '@/components/primitives';
import { login, verifyOtp } from '@/features/auth/api';
import { otpErrorMessage } from '@/features/auth/messages';
import {
  endOtpFlow,
  maskEmail,
  otpCredentials,
  otpEmail,
  otpOrigin,
} from '@/features/auth/otpFlow';
import { useSession } from '@/features/auth/session';
import { openMailbox } from '@/lib/mailbox';
import { space, useTheme } from '@/theme';

/** Anti-abus : le renvoi n'est possible qu'après ce délai. §2.4 */
const RESEND_COOLDOWN_S = 30;
/** Durée de vie de l'OTP côté backend — contrat §1. */
const OTP_LIFETIME_S = 300;
const MAX_ATTEMPTS = 3;
const LOCKOUT_S = 60;

export default function VerifyOtpScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const adopt = useSession((state) => state.adopt);
  const consumeResumePath = useSession((state) => state.consumeResumePath);

  const email = otpEmail();
  const origin = otpOrigin();

  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [attempts, setAttempts] = useState(0);
  const [cooldown, setCooldown] = useState(RESEND_COOLDOWN_S);
  const [remaining, setRemaining] = useState(OTP_LIFETIME_S);
  const [lockout, setLockout] = useState(0);

  // NFR-44 — capture d'écran bloquée sur tout écran de code.
  useEffect(() => {
    void ScreenCapture.preventScreenCaptureAsync();
    return () => {
      void ScreenCapture.allowScreenCaptureAsync();
    };
  }, []);

  // Un seul intervalle pour les trois compteurs : §7.3 limite les boucles
  // actives par écran, et trois `setInterval` concurrents seraient du gaspillage.
  useEffect(() => {
    const timer = setInterval(() => {
      setCooldown((value) => (value > 0 ? value - 1 : 0));
      setRemaining((value) => (value > 0 ? value - 1 : 0));
      setLockout((value) => (value > 0 ? value - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  // Le parcours se termine quoi qu'il arrive : le PIN volatile ne survit pas
  // à la sortie de l'écran.
  useEffect(() => endOtpFlow, []);

  const submit = useCallback(
    async (code: string) => {
      if (!email || lockout > 0) return;

      setLoading(true);
      setError(null);
      try {
        const tokens = await verifyOtp(email, code);
        await adopt(tokens);
        endOtpFlow();
        // Reconnexion après expiration : retour **exact** à l'écran quitté,
        // jamais à l'accueil — `docs/05-screens.md` §8.1.
        const resume = consumeResumePath();
        router.replace((resume ?? '/home') as '/home');
      } catch (cause) {
        const next = attempts + 1;
        setAttempts(next);
        setError(otpErrorMessage(cause));
        if (next >= MAX_ATTEMPTS) {
          setLockout(LOCKOUT_S);
          setAttempts(0);
        }
      } finally {
        setLoading(false);
      }
    },
    [email, attempts, lockout, adopt, consumeResumePath],
  );

  /**
   * Renvoi — contrat §6 : **aucun endpoint de renvoi d'OTP n'existe**.
   *
   * Sur un parcours d'inscription, rappeler `/auth/register` renverrait `409`
   * puisque le compte existe déjà. Le renvoi passe donc par `/auth/login` dans
   * les deux cas, avec le PIN conservé en mémoire volatile pour ce seul usage.
   */
  const resend = useCallback(async () => {
    const credentials = otpCredentials();
    if (!credentials || cooldown > 0) return;

    setLoading(true);
    setError(null);
    try {
      await login(credentials.email, credentials.rawPin);
      setCooldown(RESEND_COOLDOWN_S);
      setRemaining(OTP_LIFETIME_S);
      setAttempts(0);
    } catch (cause) {
      setError(otpErrorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, [cooldown]);

  const expired = remaining === 0;
  const locked = lockout > 0;

  if (!email || !origin) {
    // Arrivée directe sans parcours actif — retour à la connexion.
    return null;
  }

  return (
    <View style={[styles.container, { backgroundColor: theme.bg.app }]}>
      <View style={[styles.nav, { paddingTop: insets.top + space[2] }]}>
        <IconButton
          name="back"
          onPress={() => {
            endOtpFlow();
            router.back();
          }}
          accessibilityLabel={t('common.goBack')}
          testID="otp-back"
        />
      </View>

      <View style={styles.body}>
        <Text variant="titleLg" align="center">
          {t('otp.title')}
        </Text>
        <Spacer size={2} />
        <Text variant="bodyMd" color="secondary" align="center">
          {t('otp.sentTo', { email: maskEmail(email) })}
        </Text>

        <Spacer size={8} />

        <OtpInput
          onComplete={(code) => void submit(code)}
          error={error}
          disabled={loading || expired || locked}
        />

        <Spacer size={4} />

        {locked ? (
          <Text variant="bodySm" tint={theme.status.failed.fg} align="center">
            {t('otp.lockedOut', { time: formatSeconds(lockout) })}
          </Text>
        ) : expired ? (
          <Text variant="bodySm" tint={theme.status.pending.fg} align="center">
            {t('otp.expired')}
          </Text>
        ) : (
          <Text variant="bodySm" color="tertiary" align="center">
            {t('otp.expiresIn', { time: formatSeconds(remaining) })}
          </Text>
        )}

        <Spacer size={8} />

        {/* Essentiel : le code arrive par e-mail, pas par SMS. Sans ce bouton,
            l'utilisateur doit chercher son client mail lui-même. */}
        <Button
          label={t('otp.openMailbox')}
          onPress={() => void openMailbox()}
          variant="secondary"
          icon="send"
        />

        <Spacer size={3} />

        <Button
          label={
            cooldown > 0
              ? t('otp.resendIn', { time: formatSeconds(cooldown) })
              : t('otp.resend')
          }
          onPress={() => void resend()}
          variant={expired ? 'primary' : 'ghost'}
          disabled={cooldown > 0 || loading}
        />
      </View>
    </View>
  );
}

function formatSeconds(total: number): string {
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return minutes > 0 ? `${minutes}:${String(seconds).padStart(2, '0')}` : `${seconds} s`;
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  nav: { height: space[16], justifyContent: 'center', paddingHorizontal: space[2] },
  body: { flex: 1, paddingHorizontal: space[5], paddingTop: space[6] },
});
