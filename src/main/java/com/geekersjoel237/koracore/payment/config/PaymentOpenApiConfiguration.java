package com.geekersjoel237.koracore.payment.config;

import com.geekersjoel237.koracore.payment.domain.enums.PaymentMethod;
import com.geekersjoel237.koracore.payment.domain.model.state.TransactionState;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class PaymentOpenApiConfiguration {

    @Bean
    OpenApiCustomizer paymentEnumerations() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            enumerate(schemas, "TransactionResponse", "state", transactionStates());
            enumerate(schemas, "CashInRequest", "paymentMethod", acceptedPaymentMethods());
            enumerate(schemas, "CashOutRequest", "paymentMethod", acceptedPaymentMethods());
            allowNull(schemas, "TransactionHistoryItem", "channel");
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void allowNull(Map<String, Schema> schemas, String schema, String property) {
        Schema enumerated = property(schemas, schema, property);
        List<Object> values = new ArrayList<>(enumerated.getEnum());
        values.add(null);
        enumerated.setEnum(values);
    }

    @SuppressWarnings("rawtypes")
    private static Schema property(Map<String, Schema> schemas, String schema, String property) {
        Schema owner = schemas.get(schema);
        if (owner == null || owner.getProperties() == null || !owner.getProperties().containsKey(property))
            throw new IllegalStateException("OpenAPI schema " + schema + "." + property + " does not exist");
        return (Schema) owner.getProperties().get(property);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void enumerate(Map<String, Schema> schemas, String schema, String property, List<String> values) {
        property(schemas, schema, property).setEnum(new ArrayList<>(values));
    }

    private static List<String> transactionStates() {
        return Arrays.stream(TransactionState.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> TransactionState.class.isAssignableFrom(field.getType()))
                .map(PaymentOpenApiConfiguration::stateOf)
                .toList();
    }

    private static String stateOf(Field field) {
        try {
            return ((TransactionState) field.get(null)).name();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> acceptedPaymentMethods() {
        Set<String> accepted = new LinkedHashSet<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            accepted.add(method.name());
            accepted.add(method.value());
        }
        return List.copyOf(accepted);
    }
}
