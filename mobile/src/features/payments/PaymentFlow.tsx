import { useCallback, useEffect, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import Animated, { FadeInDown, FadeInUp } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { router } from 'expo-router';

import { ActionBar, Button, IconButton } from '@/components/action';
import { MethodPicker } from '@/components/display';
import { PhoneField, PHONE_NUMBER_PATTERN, PHONE_PREFIX_PATTERN, PinPad } from '@/components/input';
import { Amount, AmountKeypad, SuccessCheck, SUCCESS_TIMELINE } from '@/components/money';
import { Divider, Icon, Pressable, Spacer, Surface, Text } from '@/components/primitives';
import { Dialog } from '@/components/overlay';
import { paymentMethodLabel } from '@/features/shared/labels';
import { formatMinorToString } from '@/lib/money';
import { useBalance } from '@/features/wallet/hooks';
import { radius, space, stroke, useReduceMotion, useTheme } from '@/theme';
import {
  FLOW_STEPS,
  recentRecipients,
  usePaymentFlow,
  type FlowKind,
  type FlowStep,
} from './flowStore';
import { RESULT_COPY } from './messages';
import { usePendingPolling } from './usePendingPolling';
import { useBlockBackWhileSubmitting, useSubmitPayment } from './useSubmitPayment';

const TITLES: Record<FlowKind, string> = {
  deposit: 'Déposer',
  withdraw: 'Retirer',
  send: 'Envoyer',
};

const QUICK_AMOUNTS = [5000, 10000, 25000];

export function PaymentFlow({ kind, step }: { kind: FlowKind; step: FlowStep }) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const flow = usePaymentFlow();
  const balance = useBalance();
  const { submit, submitting } = useSubmitPayment();
  const [confirmExit, setConfirmExit] = useState(false);

  useBlockBackWhileSubmitting(submitting);

  // Le parcours se réinitialise à l'entrée : rentrer par une route directe ne
  // doit jamais hériter des montants d'une opération précédente.
  useEffect(() => {
    if (flow.kind !== kind) {
      flow.begin(kind, balance.data?.balance.currency ?? 'XOF');
    }
  }, [kind, flow, balance.data]);

  // La clé d'idempotence est armée à l'entrée du récapitulatif, pas à l'envoi.
  useEffect(() => {
    if (step === 'review') flow.armIdempotency();
  }, [step, flow]);

  const steps = FLOW_STEPS[kind];
  const index = steps.indexOf(step);

  const goNext = useCallback(() => {
    const next = steps[index + 1];
    if (next) router.push(`/${kind}/${next}` as never);
  }, [steps, index, kind]);

  const close = useCallback(() => {
    flow.reset();
    router.dismissAll();
  }, [flow]);

  const requestClose = useCallback(() => {
    if (flow.amountMinor > 0 && step !== 'result') setConfirmExit(true);
    else close();
  }, [flow.amountMinor, step, close]);

  // Le solde borne le retrait et le transfert, jamais le dépôt.
  const maxMinor = kind === 'deposit' ? undefined : balance.data?.balance.minor;
  const remaining =
    maxMinor !== undefined
      ? `Solde après opération : ${formatMinorToString(Math.max(0, maxMinor - flow.amountMinor), flow.currency)}`
      : null;

  return (
    <View style={[styles.container, { backgroundColor: theme.bg.app }]}>
      <View style={[styles.nav, { paddingTop: insets.top + space[2] }]}>
        {step === 'result' ? (
          <View />
        ) : (
          <IconButton
            name={index === 0 ? 'close' : 'back'}
            onPress={() => (index === 0 ? requestClose() : router.back())}
            accessibilityLabel={index === 0 ? 'Fermer' : 'Étape précédente'}
            disabled={submitting}
            testID="flow-back"
          />
        )}
        {step !== 'result' && (
          <Text variant="titleSm">{TITLES[kind]}</Text>
        )}
        <View style={styles.navSpacer} />
      </View>

      {step === 'recipient' && <RecipientStep onNext={goNext} />}
      {step === 'amount' && (
        <AmountStep
          maxMinor={maxMinor}
          remaining={remaining}
          onNext={goNext}
          nextLabel={kind === 'send' ? 'Continuer' : 'Continuer'}
        />
      )}
      {step === 'method' && <MethodStep onNext={goNext} />}
      {step === 'review' && <ReviewStep kind={kind} onNext={goNext} />}
      {step === 'pin' && (
        <PinStep
          submitting={submitting}
          onSubmit={async (pin) => {
            await submit(pin);
            // `replace` et non `push` : la pile ne doit pas conserver l'étape
            // PIN, sinon un retour depuis le résultat permettrait de relancer.
            router.replace(`/${kind}/result` as never);
          }}
        />
      )}
      {step === 'result' && <ResultStep kind={kind} onClose={close} />}

      <Dialog
        visible={confirmExit}
        title="Abandonner l’opération ?"
        message="Le montant saisi sera perdu."
        confirmLabel="Abandonner"
        onConfirm={() => {
          setConfirmExit(false);
          close();
        }}
        cancelLabel="Continuer"
        onCancel={() => setConfirmExit(false)}
        destructive
      />
    </View>
  );
}

// ─────────────────────────────────────────────────────────────── Étapes ─────

function RecipientStep({ onNext }: { onNext: () => void }) {
  const flow = usePaymentFlow();
  const [prefix, setPrefix] = useState('+225');
  const [number, setNumber] = useState('');
  const [error, setError] = useState<string | null>(null);
  const recents = recentRecipients();

  const valid = PHONE_PREFIX_PATTERN.test(prefix) && PHONE_NUMBER_PATTERN.test(number);

  const submit = () => {
    if (!valid) {
      setError('Indicatif ou numéro invalide.');
      return;
    }
    flow.setRecipient(`${prefix}${number}`);
    onNext();
  };

  return (
    <>
      <ScrollView contentContainerStyle={styles.body}>
        <Text variant="titleLg">À qui envoyez-vous ?</Text>
        <Spacer size={6} />
        <PhoneField
          prefix={prefix}
          onPrefixChange={setPrefix}
          number={number}
          onNumberChange={(value) => {
            setNumber(value);
            setError(null);
          }}
          error={error}
          recents={recents}
          onSelectRecent={(entry) => {
            flow.setRecipient(entry);
            onNext();
          }}
        />
      </ScrollView>
      <ActionBar>
        <Button label="Continuer" onPress={submit} disabled={!valid} testID="flow-next" />
      </ActionBar>
    </>
  );
}

function AmountStep({
  maxMinor,
  remaining,
  onNext,
  nextLabel,
}: {
  maxMinor: number | undefined;
  remaining: string | null;
  onNext: () => void;
  nextLabel: string;
}) {
  const flow = usePaymentFlow();

  return (
    <>
      <AmountKeypad
        currency={flow.currency}
        value={flow.amountMinor}
        onChange={flow.setAmount}
        {...(maxMinor !== undefined && { maxMinor })}
        {...(remaining !== null && { remainingLabel: remaining })}
        quickAmounts={QUICK_AMOUNTS}
      />
      <ActionBar>
        <Button
          label={nextLabel}
          onPress={onNext}
          disabled={flow.amountMinor <= 0}
          testID="flow-next"
        />
      </ActionBar>
    </>
  );
}

function MethodStep({ onNext }: { onNext: () => void }) {
  const flow = usePaymentFlow();

  return (
    <>
      <ScrollView contentContainerStyle={styles.body}>
        <Text variant="titleLg">Quel opérateur ?</Text>
        <Spacer size={6} />
        <MethodPicker value={flow.method} onChange={flow.setMethod} />
      </ScrollView>
      <ActionBar>
        <Button label="Continuer" onPress={onNext} disabled={!flow.method} testID="flow-next" />
      </ActionBar>
    </>
  );
}

function ReviewStep({ kind, onNext }: { kind: FlowKind; onNext: () => void }) {
  const theme = useTheme();
  const flow = usePaymentFlow();
  const balance = useBalance();

  const isTransfer = kind === 'send';
  const after =
    kind === 'deposit'
      ? (balance.data?.balance.minor ?? 0) + flow.amountMinor
      : (balance.data?.balance.minor ?? 0) - flow.amountMinor;

  return (
    <>
      <ScrollView contentContainerStyle={styles.body}>
        <View style={styles.reviewAmount}>
          <Amount
            minor={flow.amountMinor}
            currency={flow.currency}
            size="displayMd"
            align="center"
          />
        </View>

        <Spacer size={8} />

        <Surface elevation={1} radius="lg" padding={5}>
          {isTransfer ? (
            <>
              <Text variant="labelMd" color="secondary">
                Destinataire
              </Text>
              <Spacer size={2} />
              {/* Groupé par blocs de deux : c'est la seule vérification
                  possible, faute d'endpoint de résolution (contrat §6.2). */}
              <Text variant="titleMd" tabular>
                {formatPhoneForReview(flow.recipientPhone)}
              </Text>
            </>
          ) : (
            <>
              <Text variant="labelMd" color="secondary">
                Opérateur
              </Text>
              <Spacer size={2} />
              <Text variant="titleMd">{paymentMethodLabel(flow.method ?? '')}</Text>
            </>
          )}

          <Spacer size={5} />
          <Divider />
          <Spacer size={5} />

          <ReviewRow label="Frais" value="Gratuit" />
          <Spacer size={3} />
          <ReviewRow
            label={kind === 'deposit' ? 'Total crédité' : 'Total débité'}
            value={formatMinorToString(flow.amountMinor, flow.currency)}
          />
          <Spacer size={3} />
          <ReviewRow
            label="Solde après"
            value={formatMinorToString(Math.max(0, after), flow.currency)}
          />
        </Surface>

        {isTransfer && (
          <>
            <Spacer size={6} />
            <Pressable
              onPress={() => flow.setVerified(!flow.verifiedRecipient)}
              haptic="select"
              scale="card"
              accessibilityRole="checkbox"
              accessibilityState={{ checked: flow.verifiedRecipient }}
              accessibilityLabel="J'ai vérifié ce numéro"
              testID="verify-recipient"
              style={styles.checkboxRow}
            >
              <View
                style={[
                  styles.checkbox,
                  {
                    borderRadius: radius.xs,
                    borderWidth: stroke.thick,
                    borderColor: flow.verifiedRecipient
                      ? theme.accent.primary
                      : theme.overlay.borderStrong,
                    backgroundColor: flow.verifiedRecipient
                      ? theme.accent.primary
                      : 'transparent',
                  },
                ]}
              >
                {flow.verifiedRecipient && (
                  <Icon name="check-circle" size="xs" color={theme.text.onAccent} />
                )}
              </View>
              <Text variant="bodyMd" style={styles.checkboxLabel}>
                J’ai vérifié ce numéro
              </Text>
            </Pressable>
          </>
        )}
      </ScrollView>

      <ActionBar>
        <Button
          label="Confirmer"
          onPress={onNext}
          disabled={isTransfer && !flow.verifiedRecipient}
          testID="flow-confirm"
        />
      </ActionBar>
    </>
  );
}

function ReviewRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.reviewRow}>
      <Text variant="bodyMd" color="secondary">
        {label}
      </Text>
      <Text variant="bodyMd" tabular>
        {value}
      </Text>
    </View>
  );
}

function PinStep({
  submitting,
  onSubmit,
}: {
  submitting: boolean;
  onSubmit: (pin: string) => Promise<void>;
}) {
  const flow = usePaymentFlow();

  return (
    <PinPad
      title="Entrez votre PIN pour confirmer"
      subtitle={formatMinorToString(flow.amountMinor, flow.currency)}
      onComplete={(pin) => void onSubmit(pin)}
      loading={submitting}
    />
  );
}

function ResultStep({ kind, onClose }: { kind: FlowKind; onClose: () => void }) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReduceMotion();
  const flow = usePaymentFlow();

  const outcome = flow.outcome ?? 'failed';
  const copy = RESULT_COPY[outcome];

  // Sondage borné : contrat §6.4. Actif uniquement sur une issue « en cours ».
  const polledState = usePendingPolling(
    flow.receipt?.id ?? null,
    outcome === 'pending',
  );

  const enter = (delay: number) =>
    reduceMotion ? undefined : FadeInDown.delay(delay).springify().damping(20).stiffness(200);

  return (
    <View style={styles.result}>
      <View style={styles.resultBody}>
        <Animated.View {...(!reduceMotion && { entering: FadeInUp.duration(280) })}>
          {outcome === 'success' ? (
            <SuccessCheck />
          ) : outcome === 'failed' ? (
            <SuccessCheck failed />
          ) : (
            <Icon
              name={outcome === 'uncertain' ? 'info' : 'clock'}
              size="xl"
              color={theme.status[outcome === 'uncertain' ? 'reversed' : 'pending'].fg}
            />
          )}
        </Animated.View>

        <Spacer size={8} />

        <Animated.View {...(enter(SUCCESS_TIMELINE.amount) && { entering: enter(SUCCESS_TIMELINE.amount) })}>
          <Text variant="titleMd" align="center">
            {copy.title}
          </Text>
        </Animated.View>

        {outcome === 'success' && (
          <>
            <Spacer size={4} />
            <Animated.View
              {...(enter(SUCCESS_TIMELINE.counterpart) && {
                entering: enter(SUCCESS_TIMELINE.counterpart),
              })}
            >
              <Amount
                minor={flow.amountMinor}
                currency={flow.currency}
                size="displayMd"
                align="center"
              />
            </Animated.View>
          </>
        )}

        {(copy.description || flow.errorMessage) && (
          <>
            <Spacer size={3} />
            <View style={styles.resultDescription}>
              <Text variant="bodyMd" color="secondary" align="center">
                {flow.errorMessage ?? copy.description}
              </Text>
            </View>
          </>
        )}

        {outcome === 'pending' && polledState && (
          <>
            <Spacer size={3} />
            <Text variant="bodySm" color="tertiary" align="center">
              État actuel : {polledState}
            </Text>
          </>
        )}

        {flow.receipt && (
          <>
            <Spacer size={4} />
            <Text variant="monoMd" color="tertiary" align="center">
              {flow.receipt.reference}
            </Text>
          </>
        )}
      </View>

      <View style={[styles.resultActions, { paddingBottom: insets.bottom + space[6] }]}>
        {outcome === 'uncertain' ? (
          <>
            {/* ⚠️ L'action principale est la VÉRIFICATION, jamais le rejeu :
                sans idempotence serveur, un rejeu peut débiter deux fois. */}
            <Button
              label={copy.primary}
              onPress={() => {
                flow.reset();
                router.dismissAll();
                router.push('/activity');
              }}
              testID="result-primary"
            />
            <Spacer size={3} />
            <Text variant="bodySm" color="tertiary" align="center">
              Réessayer pourrait créer une seconde opération.
            </Text>
            <Spacer size={2} />
            <Button
              label={copy.secondary}
              variant="ghost"
              onPress={() => router.replace(`/${kind}/review` as never)}
              testID="result-secondary"
            />
          </>
        ) : outcome === 'failed' ? (
          <>
            <Button
              label={copy.primary}
              onPress={() => router.replace(`/${kind}/review` as never)}
              testID="result-primary"
            />
            <Spacer size={2} />
            <Button label={copy.secondary} variant="ghost" onPress={onClose} testID="result-secondary" />
          </>
        ) : (
          <>
            <Button label={copy.primary} onPress={onClose} testID="result-primary" />
            <Spacer size={2} />
            <Button
              label={copy.secondary}
              variant="ghost"
              onPress={() => {
                const id = flow.receipt?.id;
                flow.reset();
                router.dismissAll();
                if (id) router.push(`/transaction/${id}` as never);
              }}
              testID="result-secondary"
            />
          </>
        )}
      </View>
    </View>
  );
}

/** `+2250708091011` → `+225 07 08 09 10 11`. */
function formatPhoneForReview(full: string): string {
  const match = /^(\+\d{1,4})(\d+)$/.exec(full);
  if (!match) return full;
  return `${match[1]} ${match[2]!.replace(/(\d{2})(?=\d)/g, '$1 ')}`;
}

const CHECKBOX = 24;

const styles = StyleSheet.create({
  container: { flex: 1 },
  nav: {
    height: 96,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space[2],
  },
  navSpacer: { width: 44 },
  body: { paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[16] },
  reviewAmount: { alignItems: 'center' },
  reviewRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  checkboxRow: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  checkbox: {
    width: CHECKBOX,
    height: CHECKBOX,
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkboxLabel: { flex: 1 },
  result: { flex: 1 },
  resultBody: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: space[6] },
  resultDescription: { maxWidth: 300 },
  resultActions: { paddingHorizontal: space[5] },
});