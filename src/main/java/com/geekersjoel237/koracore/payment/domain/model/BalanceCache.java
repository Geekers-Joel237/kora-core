package com.geekersjoel237.koracore.payment.domain.model;

import com.geekersjoel237.koracore.payment.domain.enums.ResourceType;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;

import java.util.ArrayList;
import java.util.List;


public final class BalanceCache {

    private BalanceCache() {
    }

   
    public static List<Account> post(Account debited, Account credited, Amount amount) {
        List<Account> changed = new ArrayList<>(2);
        if (isCached(debited)) {
            debited.debit(amount);
            changed.add(debited);
        }
        if (isCached(credited)) {
            credited.credit(amount);
            changed.add(credited);
        }
        return List.copyOf(changed);
    }

    public static List<Account> reverse(Account debited, Account credited, Amount amount) {
        List<Account> changed = new ArrayList<>(2);
        if (isCached(debited)) {
            debited.credit(amount);
            changed.add(debited);
        }
        if (isCached(credited)) {
            credited.debit(amount);
            changed.add(credited);
        }
        return List.copyOf(changed);
    }

    private static boolean isCached(Account account) {
        return account.snapshot().accountType().resourceType() == ResourceType.CUSTOMER_ACCOUNT;
    }
}
