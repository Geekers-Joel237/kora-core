import { useLocalSearchParams } from 'expo-router';

import { PaymentFlow } from '@/features/payments/PaymentFlow';
import type { FlowStep } from '@/features/payments/flowStore';

export default function DepositFlowRoute() {
  const { step } = useLocalSearchParams<{ step: FlowStep }>();
  return <PaymentFlow kind="deposit" step={step ?? 'amount'} />;
}
