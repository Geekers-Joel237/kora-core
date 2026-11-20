package com.geekersjoel237.koracore.payment.contract;

import com.geekersjoel237.koracore.payment.adapters.in.rest.api.balance.BalanceAction;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashIn.CashInAction;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.cashOut.CashOutAction;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.history.HistoryAction;
import com.geekersjoel237.koracore.payment.adapters.in.rest.api.transfer.TransferAction;
import com.geekersjoel237.koracore.payment.config.PaymentOpenApiConfiguration;
import com.geekersjoel237.koracore.shared.config.OpenApiConfiguration;
import com.geekersjoel237.koracore.shared.contract.ContractTest;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.geekersjoel237.koracore.shared.contract.ContractRequests.get;
import static com.geekersjoel237.koracore.shared.contract.ContractSnapshot.ROOT;
import static com.geekersjoel237.koracore.shared.contract.ContractSnapshot.approve;
import static com.geekersjoel237.koracore.shared.contract.ContractSnapshot.body;
import static com.geekersjoel237.koracore.shared.contract.ContractSnapshot.read;
import static org.assertj.core.api.Assertions.assertThat;

@ContractTest({CashInAction.class, CashOutAction.class, TransferAction.class, BalanceAction.class, HistoryAction.class})
@Import({PaymentQueryDoubles.class, OpenApiConfiguration.class, PaymentOpenApiConfiguration.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
class PaymentOpenApiContractTest {

    private static final Path PUBLISHED = Path.of("api-contract", "payments.openapi.json");
    private static final Path PAYMENT_CONTRACTS = ROOT.resolve("payment");

    @Autowired
    private MockMvcTester mvc;

    @Test
    void the_published_specification_is_the_committed_one() {
        approve(PUBLISHED, specification());
    }

    @Test
    void every_response_the_contracts_observe_is_declared_and_nothing_else() {
        assertThat(declared(specification())).isEqualTo(observed());
    }

    private JsonNode specification() {
        return body(get(mvc, "/v3/api-docs", null));
    }

    private static Map<String, Set<String>> declared(JsonNode specification) {
        Map<String, Set<String>> responses = new TreeMap<>();
        specification.get("paths").properties().forEach(path ->
                path.getValue().properties().forEach(operation -> {
                    String key = operation.getKey().toUpperCase() + " " + path.getKey();
                    operation.getValue().get("responses").properties().forEach(response -> {
                        JsonNode content = response.getValue().get("content");
                        if (content == null)
                            responses.computeIfAbsent(key, k -> new TreeSet<>()).add(response.getKey() + " (no body)");
                        else
                            content.propertyNames().forEach(mediaType -> responses
                                    .computeIfAbsent(key, k -> new TreeSet<>())
                                    .add(response.getKey() + " " + mediaType));
                    });
                }));
        return responses;
    }

    private static Map<String, Set<String>> observed() {
        Map<String, Set<String>> responses = new TreeMap<>();
        try (Stream<Path> files = Files.walk(PAYMENT_CONTRACTS)) {
            files.filter(file -> file.toString().endsWith(".json"))
                    .filter(file -> !file.toString().endsWith(".received.json"))
                    .map(file -> read(file))
                    .forEach(contract -> responses
                            .computeIfAbsent(contract.get("operation").asString(), k -> new TreeSet<>())
                            .add(contract.get("status").asInt() + " " + contract.get("contentType").asString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return responses;
    }
}
