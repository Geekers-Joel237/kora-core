package com.geekersjoel237.koracore.payment.unit.domain.account;

import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.payment.domain.model.Account;
import com.geekersjoel237.koracore.payment.domain.model.BalanceCache;
import com.geekersjoel237.koracore.shared.domain.vo.Amount;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BalanceCacheTest {

    private static final Amount HUNDRED = Amount.of(BigDecimal.valueOf(100), "XAF");

    private Account payer;
    private Account payee;
    private Account floatAccount;

    @BeforeEach
    void setUp() {
        payer = Account.createCustomerAccount(Id.generate(), new Id("cust-A"));
        payer.credit(Amount.of(BigDecimal.valueOf(300), "XAF"));
        payee = Account.createCustomerAccount(Id.generate(), new Id("cust-B"));
        floatAccount = Account.createFloatAccount(Id.generate(), new Id("prov-001"));
    }

    private static BigDecimal balanceOf(Account account) {
        return account.snapshot().balance().solde().value();
    }

    @Test
    void a_transfer_debits_the_payer_and_credits_the_payee() {
        List<Account> changed = BalanceCache.post(payer, payee, HUNDRED);

        assertThat(balanceOf(payer)).isEqualByComparingTo("200");
        assertThat(balanceOf(payee)).isEqualByComparingTo("100");
        assertThat(changed).containsExactly(payer, payee);
    }

    @Test
    void a_cash_in_credits_the_wallet_and_leaves_the_float_untouched() {
        List<Account> changed = BalanceCache.post(floatAccount, payee, HUNDRED);

        assertThat(balanceOf(payee)).isEqualByComparingTo("100");
        assertThat(balanceOf(floatAccount)).isEqualByComparingTo("0");
        assertThat(changed).containsExactly(payee);
    }

    @Test
    void a_cash_out_debits_the_wallet_and_leaves_the_float_untouched() {
        List<Account> changed = BalanceCache.post(payer, floatAccount, HUNDRED);

        assertThat(balanceOf(payer)).isEqualByComparingTo("200");
        assertThat(balanceOf(floatAccount))
                .describedAs("a credit to the float would make its stored balance lie")
                .isEqualByComparingTo("0");
        assertThat(changed).containsExactly(payer);
    }

    @Test
    void a_wallet_cannot_be_debited_below_zero() {
        assertThatThrownBy(() -> BalanceCache.post(payee, payer, HUNDRED))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void reversing_a_transfer_gives_the_payer_back_what_the_payee_received() {
        BalanceCache.post(payer, payee, HUNDRED);

        List<Account> changed = BalanceCache.reverse(payer, payee, HUNDRED);

        assertThat(balanceOf(payer)).isEqualByComparingTo("300");
        assertThat(balanceOf(payee)).isEqualByComparingTo("0");
        assertThat(changed).containsExactly(payer, payee);
    }

    @Test
    void reversing_a_cash_in_takes_it_back_from_the_wallet_and_leaves_the_float_untouched() {
        BalanceCache.post(floatAccount, payee, HUNDRED);

        List<Account> changed = BalanceCache.reverse(floatAccount, payee, HUNDRED);

        assertThat(balanceOf(payee)).isEqualByComparingTo("0");
        assertThat(balanceOf(floatAccount)).isEqualByComparingTo("0");
        assertThat(changed).containsExactly(payee);
    }

    @Test
    void reversing_a_cash_out_gives_it_back_to_the_wallet_and_leaves_the_float_untouched() {
        BalanceCache.post(payer, floatAccount, HUNDRED);

        List<Account> changed = BalanceCache.reverse(payer, floatAccount, HUNDRED);

        assertThat(balanceOf(payer)).isEqualByComparingTo("300");
        assertThat(balanceOf(floatAccount)).isEqualByComparingTo("0");
        assertThat(changed).containsExactly(payer);
    }

    @Test
    void a_reversal_cannot_take_back_what_the_payee_has_already_spent() {
        BalanceCache.post(payer, payee, HUNDRED);
        payee.debit(HUNDRED);

        assertThatThrownBy(() -> BalanceCache.reverse(payer, payee, HUNDRED))
                .isInstanceOf(InsufficientFundsException.class);
    }
}
