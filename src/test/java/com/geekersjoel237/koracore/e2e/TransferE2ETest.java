package com.geekersjoel237.koracore.e2e;

import com.geekersjoel237.koracore.web.api.payment.CashInRequest;
import com.geekersjoel237.koracore.web.api.payment.TransactionResponse;
import com.geekersjoel237.koracore.web.api.payment.TransferRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransferE2ETest extends AbstractE2ETest {

    private static final BigDecimal CASH_IN_AMOUNT  = new BigDecimal("10000.00");
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("3000.00");

    @Test
    void should_transfer_funds_between_two_customers() {
        SetupData sender   = setupCustomerWithAccount("sender@example.com",   "Sender",   "+225", "07000003001", "1234");
        SetupData receiver = setupCustomerWithAccount("receiver@example.com", "Receiver", "+225", "07000003002", "5678");

        // Fund sender first
        postWithToken("/payments/cash-in",
                new CashInRequest("1234", CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                sender.tokens().accessToken(), TransactionResponse.class);

        String receiverPhone = "+22507000003002";
        ResponseEntity<TransactionResponse> response = postWithToken(
                "/payments/transfer",
                new TransferRequest("1234", TRANSFER_AMOUNT, "XOF", "ORANGE_MONEY", receiverPhone),
                sender.tokens().accessToken(),
                TransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().state()).isEqualTo("COMPLETED");
    }

    @Test
    void should_decrease_sender_and_increase_receiver_balance() {
        SetupData sender   = setupCustomerWithAccount("snd2@example.com", "Sender2",   "+225", "07000003003", "1234");
        SetupData receiver = setupCustomerWithAccount("rcv2@example.com", "Receiver2", "+225", "07000003004", "5678");

        postWithToken("/payments/cash-in",
                new CashInRequest("1234", CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                sender.tokens().accessToken(), TransactionResponse.class);

        postWithToken("/payments/transfer",
                new TransferRequest("1234", TRANSFER_AMOUNT, "XOF", "ORANGE_MONEY", "+22507000003004"),
                sender.tokens().accessToken(), TransactionResponse.class);

        var senderAccount   = accountRepository.findByCustomerId(sender.customerId()).orElseThrow();
        var receiverAccount = accountRepository.findByCustomerId(receiver.customerId()).orElseThrow();

        assertThat(senderAccount.snapshot().balance().solde().value())
                .isEqualByComparingTo(CASH_IN_AMOUNT.subtract(TRANSFER_AMOUNT));
        assertThat(receiverAccount.snapshot().balance().solde().value())
                .isEqualByComparingTo(TRANSFER_AMOUNT);
    }

    @Test
    void should_return_422_for_insufficient_funds() {
        SetupData sender   = setupCustomerWithAccount("snd3@example.com", "Sender3",   "+225", "07000003005", "1234");
        setupCustomerWithAccount("rcv3@example.com", "Receiver3", "+225", "07000003006", "5678");

        // No cash-in → balance is 0
        ResponseEntity<String> response = postWithToken(
                "/payments/transfer",
                new TransferRequest("1234", TRANSFER_AMOUNT, "XOF", "ORANGE_MONEY", "+22507000003006"),
                sender.tokens().accessToken(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void should_return_404_for_nonexistent_recipient() {
        SetupData sender = setupCustomerWithAccount("snd4@example.com", "Sender4", "+225", "07000003007", "1234");

        postWithToken("/payments/cash-in",
                new CashInRequest("1234", CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                sender.tokens().accessToken(), TransactionResponse.class);

        ResponseEntity<String> response = postWithToken(
                "/payments/transfer",
                new TransferRequest("1234", TRANSFER_AMOUNT, "XOF", "ORANGE_MONEY", "+22500000000001"),
                sender.tokens().accessToken(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_return_422_for_self_transfer() {
        SetupData sender = setupCustomerWithAccount("snd5@example.com", "Sender5", "+225", "07000003009", "1234");

        postWithToken("/payments/cash-in",
                new CashInRequest("1234", CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                sender.tokens().accessToken(), TransactionResponse.class);

        ResponseEntity<String> response = postWithToken(
                "/payments/transfer",
                new TransferRequest("1234", TRANSFER_AMOUNT, "XOF", "ORANGE_MONEY", "+22507000003009"),
                sender.tokens().accessToken(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void should_return_401_with_wrong_pin_on_transfer() {
        SetupData sender   = setupCustomerWithAccount("snd6@example.com", "Sender6",   "+225", "07000003010", "1234");
        setupCustomerWithAccount("rcv6@example.com", "Receiver6", "+225", "07000003011", "5678");

        postWithToken("/payments/cash-in",
                new CashInRequest("1234", CASH_IN_AMOUNT, "XOF", "ORANGE_MONEY"),
                sender.tokens().accessToken(), TransactionResponse.class);

        ResponseEntity<String> response = postWithToken(
                "/payments/transfer",
                new TransferRequest("0000", TRANSFER_AMOUNT, "XOF", "ORANGE_MONEY", "+22507000003011"),
                sender.tokens().accessToken(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}