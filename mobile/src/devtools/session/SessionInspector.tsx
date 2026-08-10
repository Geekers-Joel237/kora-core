import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/action';
import { Divider, Spacer, Surface, Text } from '@/components/primitives';
import { refresh as refreshApi } from '@/features/auth/api';
import { useSession } from '@/features/auth/session';
import { decodeAccessToken } from '@/lib/jwt';
import { space, useTheme } from '@/theme';

/** Sous ce seuil, le compte à rebours passe en rouge — §8. */
const CRITICAL_S = 60;

/**
 * Inspecteur de session — `docs/10-validation-mode.md` §8.
 *
 * « Le compte à rebours visible transforme la validation du rafraîchissement de
 * jeton, qui est autrement une attente aveugle de quinze minutes, en
 * observation directe. »
 *
 * ⚠️ **Les jetons ne sont jamais affichés en clair, ni copiables.** Uniquement
 * leurs claims décodés. Cette règle du §8 est la raison pour laquelle cet écran
 * n'expose aucun bouton de copie.
 */
export function SessionInspector() {
  const theme = useTheme();
  const tokens = useSession((state) => state.tokens);
  const user = useSession((state) => state.user);
  const expired = useSession((state) => state.expired);
  const adopt = useSession((state) => state.adopt);
  const markExpired = useSession((state) => state.markExpired);

  const [now, setNow] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  // Une seule boucle pour les deux comptes à rebours — §7.3 limite les
  // intervalles actifs par écran.
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  if (!tokens) {
    return (
      <View style={styles.empty}>
        <Text variant="bodyMd" color="secondary">
          Aucune session active.
        </Text>
      </View>
    );
  }

  const claims = decodeAccessToken(tokens.accessToken);
  const accessLeft = Math.floor((tokens.accessTokenExpiry.getTime() - now) / 1000);
  const refreshLeft = Math.floor((tokens.refreshTokenExpiry.getTime() - now) / 1000);

  const forceRefresh = async () => {
    setBusy(true);
    setMessage(null);
    try {
      const renewed = await refreshApi(tokens.refreshToken);
      await adopt(renewed);
      setMessage('Jetons renouvelés.');
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : 'Rafraîchissement en échec.');
    } finally {
      setBusy(false);
    }
  };

  /**
   * Invalide l'accès **sans toucher au rafraîchissement** : la prochaine requête
   * doit recevoir un `401`, déclencher un rafraîchissement unique et se rejouer
   * sans que l'utilisateur ne voie quoi que ce soit. C'est le scénario 10 du §11.
   */
  const invalidateAccess = () => {
    useSession.setState({
      tokens: { ...tokens, accessToken: 'invalide.invalide.invalide' },
    });
    setMessage('Jeton d’accès invalidé — la prochaine requête doit se rejouer seule.');
  };

  /** Les deux jetons morts : la feuille de session expirée doit remonter. */
  const invalidateBoth = () => {
    void markExpired(null);
    setMessage('Session expirée forcée — la feuille de reconnexion doit apparaître.');
  };

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      <Surface elevation={1} padding={4}>
        <Text variant="labelMd">Jeton d’accès</Text>
        <Spacer size={2} />
        <Claim label="sub" value={claims?.sub ?? '—'} />
        <Claim label="email" value={claims?.email ?? '—'} />
        <Claim label="role" value={claims?.role ?? '—'} />
        <Claim label="jti" value={claims?.jti ?? '—'} />
        <Divider />
        <Spacer size={2} />
        <Countdown label="Expire dans" seconds={accessLeft} theme={theme} />
      </Surface>

      <Spacer size={4} />

      <Surface elevation={1} padding={4}>
        <Text variant="labelMd">Jeton de rafraîchissement</Text>
        <Spacer size={2} />
        <Countdown label="Expire dans" seconds={refreshLeft} theme={theme} />
        <Spacer size={2} />
        <Text variant="bodySm" color="tertiary">
          Contrat §6.5 — aucune invalidation serveur. Un jeton volé reste valide
          jusqu’à cette échéance.
        </Text>
      </Surface>

      <Spacer size={4} />

      <Surface elevation={1} padding={4}>
        <Text variant="labelMd">Profil reconstitué</Text>
        <Spacer size={2} />
        <Claim label="id" value={user?.id ?? '—'} />
        <Claim label="session expirée" value={expired ? 'oui' : 'non'} />
      </Surface>

      <Spacer size={5} />

      <Button
        label="Forcer un rafraîchissement"
        variant="secondary"
        loading={busy}
        onPress={() => void forceRefresh()}
        testID="session-force-refresh"
      />
      <Spacer size={3} />
      <Button
        label="Invalider le jeton d’accès"
        variant="ghost"
        onPress={invalidateAccess}
        testID="session-invalidate-access"
      />
      <Spacer size={2} />
      <Button
        label="Tout invalider"
        variant="danger"
        onPress={invalidateBoth}
        testID="session-invalidate-all"
      />

      {message && (
        <>
          <Spacer size={4} />
          <Text variant="bodySm" color="secondary">
            {message}
          </Text>
        </>
      )}

      <Spacer size={4} />
      <Text variant="bodySm" color="tertiary">
        Les jetons ne sont jamais affichés en clair ni copiables — §8.
      </Text>
    </ScrollView>
  );
}

function Claim({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.claim}>
      <Text variant="bodySm" color="tertiary">
        {label}
      </Text>
      <Text variant="monoMd" numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

function Countdown({
  label,
  seconds,
  theme,
}: {
  label: string;
  seconds: number;
  theme: ReturnType<typeof useTheme>;
}) {
  const expired = seconds <= 0;
  const critical = seconds <= CRITICAL_S;

  return (
    <View style={styles.claim}>
      <Text variant="bodySm" color="tertiary">
        {label}
      </Text>
      <Text
        variant="monoMd"
        tabular
        tint={expired || critical ? theme.status.failed.fg : theme.text.primary}
      >
        {expired ? 'expiré' : formatCountdown(seconds)}
      </Text>
    </View>
  );
}

function formatCountdown(total: number): string {
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingBottom: space[8] },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  claim: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space[4],
    paddingVertical: space[1],
  },
});
