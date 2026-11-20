package com.geekersjoel237.koracore.payment.adapters.in.rest.mapping;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn.CashInRequest;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashOut.CashOutRequest;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.reverse.ReverseRequest;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.transfer.TransferRequest;
import com.geekersjoel237.koracore.payment.application.command.CashInCommand;
import com.geekersjoel237.koracore.payment.application.command.CashOutCommand;
import com.geekersjoel237.koracore.payment.application.command.ReversePaymentCommand;
import com.geekersjoel237.koracore.payment.application.command.TransferCommand;
import com.geekersjoel237.koracore.shared.adapters.in.rest.mapping.RequestMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static com.geekersjoel237.koracore.shared.adapters.in.rest.mapping.RequestConversions.CORRELATION_ID;

@Mapper(config = RequestMapperConfig.class, uses = PaymentRequestConversions.class)
public interface PaymentRequestMapper {

    @Mapping(target = "correlationId", source = "correlationId", qualifiedByName = CORRELATION_ID)
    @Mapping(target = "customerId", source = "customerId")
    @Mapping(target = "pin", source = "request.rawPin")
    @Mapping(target = "amount.value", source = "request.amount")
    @Mapping(target = "amount.currency", source = "request.currency")
    @Mapping(target = "paymentMethod", source = "request.paymentMethod")
    CashInCommand toCommand(CashInRequest request, String customerId, String correlationId);

    @Mapping(target = "correlationId", source = "correlationId", qualifiedByName = CORRELATION_ID)
    @Mapping(target = "customerId", source = "customerId")
    @Mapping(target = "pin", source = "request.rawPin")
    @Mapping(target = "amount.value", source = "request.amount")
    @Mapping(target = "amount.currency", source = "request.currency")
    @Mapping(target = "paymentMethod", source = "request.paymentMethod")
    CashOutCommand toCommand(CashOutRequest request, String customerId, String correlationId);

    @Mapping(target = "correlationId", source = "correlationId", qualifiedByName = CORRELATION_ID)
    @Mapping(target = "customerId", source = "customerId")
    @Mapping(target = "pin", source = "request.rawPin")
    @Mapping(target = "amount.value", source = "request.amount")
    @Mapping(target = "amount.currency", source = "request.currency")
    @Mapping(target = "recipient.prefix", source = "request.toPhonePrefix")
    @Mapping(target = "recipient.number", source = "request.toPhoneNumber")
    TransferCommand toCommand(TransferRequest request, String customerId, String correlationId);

    @Mapping(target = "transactionId", source = "transactionId")
    @Mapping(target = "actorId", source = "request.actorId")
    @Mapping(target = "actorRole", source = "request.actorRole")
    @Mapping(target = "reason", source = "request.reason")
    @Mapping(target = "correlationId", source = "request.correlationId")
    ReversePaymentCommand toCommand(ReverseRequest request, String transactionId);
}
