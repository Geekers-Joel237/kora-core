import { useLocalSearchParams } from 'expo-router';

import { PaymentFlow } from '@/features/payments/PaymentFlow';
import type { FlowStep } from '@/features/payments/flowStore';

export default function WithdrawFlowRoute() {
  const { step } = useLocalSearchParams<{ step: FlowStep }>();
  return <PaymentFlow kind="withdraw" step={step ?? 'amount'} />;
}
