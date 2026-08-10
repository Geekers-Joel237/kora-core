import { useCallback, useEffect, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Constants from 'expo-constants';

import { Button } from '@/components/action';
import { Segmented, Toggle } from '@/components/input';
import { Dialog } from '@/components/overlay';
import { Divider, Pressable, Spacer, Surface, Text } from '@/components/primitives';
import { DEV_MODE, devtoolsTrigger, openDevtools } from '@/devtools';
import {
  probeBiometrics,
  setBiometricsEnabled,
  useBiometrics,
} from '@/features/auth/biometrics';
import { useSession } from '@/features/auth/session';
import { useBalance } from '@/features/wallet/hooks';
import {
  languagePreference,
  setLanguagePreference,
  type Language,
  type LanguagePreference,
} from '@/i18n';
import { setBalanceHidden, setHapticsPreference, usePreferences } from '@/lib/preferences';
import {
  layout,
  radius,
  space,
  useTheme,
  useThemeContext,
  type SpaceToken,
  type ThemePreference,
} from '@/theme';

const AVATAR_SIZE = 56;

/**
 * Réglages — `docs/05-screens.md` §7.
 *
 * Le profil est **reconstitué**, jamais lu : aucun `GET /me` n'existe. Le nom
 * et le téléphone viennent du stockage local, l'e-mail des claims du jeton, le
 * numéro de compte de `GET /payments/balance`. Contrat §6.3.
 *
 * Conséquence assumée : quelqu'un qui se connecte sur un nouvel appareil n'a
 * pas son nom complet. On affiche l'e-mail — on ne fabrique pas de nom.
 */
export default function SettingsScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const { preference: themePreference, setPreference: setThemePreference } = useThemeContext();
  const profile = useSession((state) => state.profile);
  const user = useSession((state) => state.user);
  const signOut = useSession((state) => state.signOut);
  const balance = useBalance();

  const biometricsEnabled = useBiometrics((state) => state.enabled);
  const biometricsAvailable = useBiometrics((state) => state.available);

  const [language, setLanguage] = useState<LanguagePreference>(languagePreference);
  const hideBalance = usePreferences((state) => state.balanceHidden);
  const hapticsEnabled = usePreferences((state) => state.hapticsEnabled);
  const [confirmSignOut, setConfirmSignOut] = useState(false);

  useEffect(() => {
    void probeBiometrics();
  }, []);

  const changeLanguage = useCallback((next: LanguagePreference | null) => {
    const value = next ?? 'system';
    setLanguage(value);
    setLanguagePreference(value);
  }, []);

  const displayName = profile.fullName ?? user?.email ?? t('settings.noName');

  return (
    <ScrollView
      style={{ backgroundColor: theme.bg.app }}
      contentContainerStyle={[
        styles.content,
        { paddingTop: insets.top + space[6], paddingBottom: insets.bottom + space[16] },
      ]}
      showsVerticalScrollIndicator={false}
    >
      <Text variant="titleLg">{t('settings.title')}</Text>

      <Section title={t('settings.profile')}>
        <Surface elevation={1} radius="lg" padding={5}>
          <View style={styles.profile}>
            <View
              style={[
                styles.avatar,
                { backgroundColor: theme.accent.wash, borderRadius: radius.full },
              ]}
            >
              <Text variant="titleMd" color="accent">
                {initials(displayName)}
              </Text>
            </View>
            <View style={styles.profileBody}>
              <Text variant="titleSm" numberOfLines={1}>
                {displayName}
              </Text>
              <Text variant="bodySm" color="tertiary" numberOfLines={1}>
                {user?.email ?? t('settings.noEmail')}
              </Text>
            </View>
          </View>

          <Spacer size={4} />
          <Divider />
          <InfoRow label={t('settings.phone')} value={profile.phone} />
          <Divider />
          <InfoRow
            label={t('settings.accountNumber')}
            value={balance.data?.number ?? null}
            mono
          />
        </Surface>
      </Section>

      <Section title={t('settings.security')}>
        <Surface elevation={1} radius="lg" padding={0}>
          <ToggleRow
            label={t('settings.biometrics')}
            description={
              biometricsAvailable ? t('settings.biometricsHint') : t('settings.biometricsUnavailable')
            }
            value={biometricsEnabled && biometricsAvailable}
            onChange={setBiometricsEnabled}
            disabled={!biometricsAvailable}
            testID="toggle-biometrics"
          />
          <Divider inset={5} />
          {/* Le backend n'expose aucun changement de PIN (contrat §6) : la ligne
              est visible et inerte plutôt qu'absente — l'absence donnerait à
              croire que la fonction n'est pas prévue. */}
          <ToggleRow
            label={t('settings.changePin')}
            description={t('settings.changePinUnavailable')}
            value={false}
            onChange={() => undefined}
            disabled
            testID="toggle-change-pin"
          />
        </Surface>
      </Section>

      <Section title={t('settings.preferences')}>
        <Surface elevation={1} radius="lg" padding={5}>
          <Text variant="labelMd" color="secondary">
            {t('settings.theme')}
          </Text>
          <Spacer size={2} />
          <Segmented
            options={[
              { value: 'system' as ThemePreference, label: t('settings.themeSystem') },
              { value: 'dark' as ThemePreference, label: t('settings.themeDark') },
              { value: 'light' as ThemePreference, label: t('settings.themeLight') },
            ]}
            value={themePreference}
            onChange={(next) => setThemePreference(next ?? 'system')}
            accessibilityLabel={t('settings.theme')}
            testID="setting-theme"
          />

          <Spacer size={5} />

          <Text variant="labelMd" color="secondary">
            {t('settings.language')}
          </Text>
          <Spacer size={2} />
          <Segmented
            options={[
              { value: 'system' as LanguagePreference, label: t('settings.languageSystem') },
              { value: 'fr' as Language, label: 'Français' },
              { value: 'en' as Language, label: 'English' },
            ]}
            value={language}
            onChange={changeLanguage}
            accessibilityLabel={t('settings.language')}
            testID="setting-language"
          />

          <Spacer size={5} />
          <Divider />
          <ToggleRow
            label={t('settings.hideBalance')}
            value={hideBalance}
            onChange={setBalanceHidden}
            inset={0}
            testID="toggle-hide-balance"
          />
          <Divider />
          {/* Barre de qualité §2 — l'haptique doit être intégralement
              désactivable. Elle reste active sous « réduire les animations » :
              la couper là retirerait du retour à qui en a le plus besoin. */}
          <ToggleRow
            label={t('settings.haptics')}
            description={t('settings.hapticsHint')}
            value={hapticsEnabled}
            onChange={setHapticsPreference}
            inset={0}
            testID="toggle-haptics"
          />
        </Surface>
      </Section>

      <Section title={t('settings.about')}>
        <Surface elevation={1} radius="lg" padding={0}>
          {/* Appui triple sur la version — déclencheur discret du §2.
              Inerte en production. */}
          <Pressable
            onPress={devtoolsTrigger.versionTap}
            haptic="none"
            scale="card"
            accessibilityLabel={t('settings.version')}
            testID="settings-version"
          >
            <InfoRow
              label={t('settings.version')}
              value={Constants.expoConfig?.version ?? null}
              inset={5}
            />
          </Pressable>
          <Divider inset={5} />
          <InfoRow label={t('settings.terms')} value={t('settings.legalPending')} inset={5} />
          <Divider inset={5} />
          <InfoRow label={t('settings.privacy')} value={t('settings.legalPending')} inset={5} />
        </Surface>
      </Section>

      {DEV_MODE && (
        <Section title={t('settings.validationMode')}>
          {/* Entrée provisoire. Les vrais déclencheurs — secousse, appui long
              sur le logo, triple appui sur la version — relèvent du lot 1bis. */}
          <Button
            label={t('settings.validationMode')}
            variant="secondary"
            icon="settings"
            onPress={() => openDevtools()}
            testID="open-devtools"
          />
        </Section>
      )}

      <Section title={t('settings.session')}>
        <Button
          label={t('settings.signOut')}
          variant="danger"
          onPress={() => setConfirmSignOut(true)}
          testID="sign-out"
        />
      </Section>

      <Dialog
        visible={confirmSignOut}
        title={t('settings.signOutTitle')}
        // Contrat §6.5 — aucune invalidation serveur : la déconnexion est
        // strictement locale, et le dire évite une fausse promesse.
        message={t('settings.signOutMessage')}
        confirmLabel={t('settings.signOut')}
        onConfirm={() => {
          setConfirmSignOut(false);
          void signOut();
        }}
        onCancel={() => setConfirmSignOut(false)}
        destructive
      />
    </ScrollView>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text variant="labelSm" color="tertiary">
        {title.toUpperCase()}
      </Text>
      <Spacer size={3} />
      {children}
    </View>
  );
}

function InfoRow({
  label,
  value,
  mono = false,
  inset = 0,
}: {
  label: string;
  value: string | null;
  mono?: boolean;
  inset?: SpaceToken | 0;
}) {
  const { t } = useTranslation();

  return (
    <View style={[styles.row, inset !== 0 && { paddingHorizontal: space[inset] }]}>
      <Text variant="bodyMd" color="secondary">
        {label}
      </Text>
      <Text
        variant={mono ? 'monoMd' : 'bodyMd'}
        color={value === null ? 'tertiary' : 'primary'}
        numberOfLines={1}
      >
        {value ?? t('settings.notAvailable')}
      </Text>
    </View>
  );
}

function ToggleRow({
  label,
  description,
  value,
  onChange,
  disabled = false,
  inset = 5,
  testID,
}: {
  label: string;
  description?: string;
  value: boolean;
  onChange: (value: boolean) => void;
  disabled?: boolean;
  inset?: SpaceToken | 0;
  testID?: string;
}) {
  return (
    <View style={[styles.row, inset !== 0 && { paddingHorizontal: space[inset] }]}>
      <View style={styles.toggleBody}>
        <Text variant="bodyMd" color={disabled ? 'disabled' : 'primary'}>
          {label}
        </Text>
        {description !== undefined && (
          <Text variant="bodySm" color="tertiary">
            {description}
          </Text>
        )}
      </View>
      <Toggle
        value={value}
        onChange={onChange}
        disabled={disabled}
        accessibilityLabel={label}
        {...(testID !== undefined && { testID })}
      />
    </View>
  );
}

/** `Aminata Diallo` → `AD`. Un e-mail donne sa première lettre, rien de plus. */
function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return (parts[0] ?? '').slice(0, 1).toUpperCase();
  return `${(parts[0] ?? '').slice(0, 1)}${(parts[1] ?? '').slice(0, 1)}`.toUpperCase();
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: space[5] },
  section: { marginTop: space[8] },
  profile: { flexDirection: 'row', alignItems: 'center', gap: space[4] },
  avatar: {
    width: AVATAR_SIZE,
    height: AVATAR_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  profileBody: { flex: 1, gap: space[1] },
  row: {
    minHeight: layout.minTouchTarget,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space[4],
    paddingVertical: space[3],
  },
  toggleBody: { flex: 1, gap: space[1] },
});
