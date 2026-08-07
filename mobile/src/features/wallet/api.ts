/** API portefeuille — contrat §2. */

import { decode } from '@/lib/decode';
import { request } from '@/lib/http';
import { balanceSchema } from '@/features/shared/schemas';
import { toAccount } from '@/features/shared/mappers';
import type { Account } from '@/types/domain';

export async function getBalance(): Promise<Account> {
  const payload = await request<unknown>('/payments/balance');
  return toAccount(decode(balanceSchema, payload, '/payments/balance'));
}
