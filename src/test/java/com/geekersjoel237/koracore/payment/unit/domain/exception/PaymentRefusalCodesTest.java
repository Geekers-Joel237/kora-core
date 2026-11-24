package com.geekersjoel237.koracore.payment.unit.domain.exception;

import com.geekersjoel237.koracore.payment.domain.enums.HoldStatus;
import com.geekersjoel237.koracore.payment.domain.exception.AccountBlockedException;
import com.geekersjoel237.koracore.payment.domain.exception.AccountNotFoundException;
import com.geekersjoel237.koracore.payment.domain.exception.AccountSuspendedException;
import com.geekersjoel237.koracore.payment.domain.exception.HoldNotActiveException;
import com.geekersjoel237.koracore.payment.domain.exception.InsufficientFundsException;
import com.geekersjoel237.koracore.payment.domain.exception.InvalidAccountException;
import com.geekersjoel237.koracore.payment.domain.exception.InvalidStateTransitionException;
import com.geekersjoel237.koracore.payment.domain.exception.PaymentProviderException;
import com.geekersjoel237.koracore.payment.domain.exception.PaymentTimeoutProviderException;
import com.geekersjoel237.koracore.payment.domain.exception.SelfTransferException;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import com.geekersjoel237.koracore.shared.domain.exception.BusinessException;
import com.geekersjoel237.koracore.shared.domain.vo.Id;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRefusalCodesTest {

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of(new AccountNotFoundException("no wallet"), "ACCOUNT_NOT_FOUND"),
                Arguments.of(new InsufficientFundsException("short"), "INSUFFICIENT_FUNDS"),
                Arguments.of(new AccountBlockedException("blocked"), "ACCOUNT_BLOCKED"),
                Arguments.of(new AccountSuspendedException("suspended"), "ACCOUNT_SUSPENDED"),
                Arguments.of(new SelfTransferException("same wallet"), "SELF_TRANSFER"),
                Arguments.of(new InvalidAccountException("not a party"), "INVALID_ACCOUNT"),
                Arguments.of(new InvalidStateTransitionException(TransactionState.REVERSED, TransactionState.REVERSED),
                        "INVALID_STATE_TRANSITION"),
                Arguments.of(new HoldNotActiveException(Id.generate(), HoldStatus.RELEASED), "HOLD_NOT_ACTIVE"),
                Arguments.of(new PaymentProviderException("refused"), "PROVIDER_REFUSED"),
                Arguments.of(new PaymentTimeoutProviderException("no answer"), "PROVIDER_TIMEOUT"));
    }

    @ParameterizedTest
    @MethodSource("refusals")
    void every_payment_refusal_names_its_code_and_the_catalog_documents_it(BusinessException refusal, String code)
            throws IOException {
        assertThat(refusal.code()).isEqualTo(code);
        assertThat(Files.readString(Path.of("docs", "problems.md")))
                .contains("### " + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
