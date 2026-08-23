import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { useTranslation } from 'react-i18next';

import { Button } from '@/components/action/Button';
import { Icon, Pressable, Spacer, Text } from '@/components/primitives';
import { isKoraError } from '@/lib/http';
import { radius, space, useTheme } from '@/theme';
import type { IconName } from '@/components/primitives';

export interface EmptyStateProps {
  icon?: IconName;
  title: string;
  description: string;
  /** **Obligatoire.** Un état vide sans issue est un cul-de-sac — §7. */
  actionLabel: string;
  onAction: () => void;
  compact?: boolean;
}

const ILLUSTRATION_SIZE = 120;

export function EmptyState({
  icon = 'activity',
  title,
  description,
  actionLabel,
  onAction,
  compact = false,
}: EmptyStateProps) {
  const theme = useTheme();

  return (
    <View style={[styles.centered, compact ? styles.compact : styles.full]}>
      <View
        style={[
          styles.illustration,
          {
            backgroundColor: theme.bg.surface2,
            borderRadius: radius.full,
            width: compact ? ILLUSTRATION_SIZE / 2 : ILLUSTRATION_SIZE,
            height: compact ? ILLUSTRATION_SIZE / 2 : ILLUSTRATION_SIZE,
          },
        ]}
      >
        <Icon name={icon} size={compact ? 'lg' : 'xl'} color={theme.text.disabled} />
      </View>

      <Spacer size={4} />
      <Text variant="titleSm" align="center">
        {title}
      </Text>
      <Spacer size={2} />
      <View style={styles.description}>
        <Text variant="bodyMd" color="secondary" align="center">
          {description}
        </Text>
      </View>

      <Spacer size={6} />
      <Button label={actionLabel} onPress={onAction} variant="secondary" size="md" fullWidth={false} />
    </View>
  );
}

export interface ErrorStateProps {
  title: string;
  description: string;
  onRetry: () => void;
  retryLabel?: string;
  /** Erreur brute — repliée par défaut, utile au support. §7 */
  error?: unknown;
  compact?: boolean;
}

/**
 * NFR-35 — toute erreur affichée indique **ce que l'utilisateur peut faire**,
 * jamais uniquement ce qui s'est mal passé.
 */
export function ErrorState({
  title,
  description,
  onRetry,
  retryLabel,
  error,
  compact = false,
}: ErrorStateProps) {
  const { t } = useTranslation();
  const theme = useTheme();
  const [expanded, setExpanded] = useState(false);

  const detail = isKoraError(error) ? (error.detail ?? error.message) : null;
  const status = isKoraError(error) ? error.status : null;

  return (
    <View style={[styles.centered, compact ? styles.compact : styles.full]}>
      <Icon name="alert-triangle" size="xl" color={theme.status.failed.fg} />

      <Spacer size={4} />
      <Text variant="titleSm" align="center">
        {title}
      </Text>
      <Spacer size={2} />
      <View style={styles.description}>
        <Text variant="bodyMd" color="secondary" align="center">
          {description}
        </Text>
      </View>

      <Spacer size={6} />
      <Button label={retryLabel ?? t('common.retry')} onPress={onRetry} size="md" fullWidth={false} />

      {detail && (
        <>
          <Spacer size={4} />
          <Pressable
            onPress={() => setExpanded((value) => !value)}
            haptic="tap"
            scale="card"
            accessibilityLabel={t('feedback.showDetailA11y')}
          >
            <Text variant="labelSm" color="tertiary">
              {expanded ? t('feedback.hideDetail') : t('feedback.technicalDetail')}
            </Text>
          </Pressable>
          {expanded && (
            <>
              <Spacer size={2} />
              <Text variant="bodySm" color="tertiary" align="center">
                {status !== null ? `${status} — ` : ''}
                {detail}
              </Text>
            </>
          )}
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  centered: { alignItems: 'center', justifyContent: 'center' },
  full: { flex: 1, padding: space[6] },
  compact: { paddingVertical: space[8], paddingHorizontal: space[4] },
  illustration: { alignItems: 'center', justifyContent: 'center' },
  description: { maxWidth: 280 },
});
