import { StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { Pressable, Spacer, Surface, Text } from '@/components/primitives';
import { radius, space, useTheme } from '@/theme';
import { TextField } from './TextField';

export interface PhoneFieldProps {
  prefix: string;
  onPrefixChange: (prefix: string) => void;
  number: string;
  onNumberChange: (number: string) => void;
  error?: string | null;
  recents?: string[];
  onSelectRecent?: (fullNumber: string) => void;
}

export const PHONE_PREFIX_PATTERN = /^\+\d{1,4}$/;
export const PHONE_NUMBER_PATTERN = /^\d{8,15}$/;

/** Groupe par blocs de deux pour la lecture — jamais pour l'envoi. */
export function groupPhone(digits: string): string {
  return digits.replace(/(\d{2})(?=\d)/g, '$1 ').trim();
}

export function PhoneField({
  prefix,
  onPrefixChange,
  number,
  onNumberChange,
  error,
  recents = [],
  onSelectRecent,
}: PhoneFieldProps) {
  const { t } = useTranslation();
  const theme = useTheme();

  return (
    <View>
      <View style={styles.row}>
        <View style={styles.prefix}>
          <TextField
            label={t('phone.prefixLabel')}
            value={prefix}
            onChangeText={onPrefixChange}
            keyboardType="phone-pad"
            testID="phone-prefix"
          />
        </View>
        <View style={styles.number}>
          <TextField
            label={t('phone.numberLabel')}
            value={number}
            // Le champ ne conserve que des chiffres : le serveur attend
            // `^\d{8,15}$` sans séparateur (contrat §1).
            onChangeText={(text) => onNumberChange(text.replace(/\D/g, ''))}
            placeholder={t('phone.numberPlaceholder')}
            keyboardType="phone-pad"
            autoComplete="tel"
            error={error}
            autoFocus
            testID="phone-number"
          />
        </View>
      </View>

      {recents.length > 0 && (
        <>
          <Spacer size={5} />
          <Text variant="labelMd" color="secondary">
            {t('phone.recents')}
          </Text>
          <Spacer size={2} />
          <View style={styles.recents}>
            {recents.map((entry) => (
              <Pressable
                key={entry}
                onPress={() => onSelectRecent?.(entry)}
                haptic="select"
                scale="card"
                accessibilityLabel={t('flow.recipient') + ' ' + entry}
                testID={`recent-${entry}`}
              >
                <Surface elevation={2} radius="full" padding={2} style={styles.chip}>
                  <Text variant="labelMd" color="secondary">
                    {entry}
                  </Text>
                </Surface>
              </Pressable>
            ))}
          </View>
        </>
      )}

      <Spacer size={4} />
      {/* Contrat §6.2 — aucun endpoint ne résout un numéro vers un nom. */}
      <Text variant="bodySm" color="tertiary" tint={theme.text.tertiary}>
        {t('phone.noNameLookup')}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: space[3] },
  prefix: { width: 104 },
  number: { flex: 1 },
  recents: { flexDirection: 'row', flexWrap: 'wrap', gap: space[2] },
  chip: { paddingHorizontal: space[4], borderRadius: radius.full },
});