package com.geekersjoel237.koracore.shared.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdLoggingE2ETest extends AbstractE2ETest {

    @Test
    void a_line_logged_before_the_command_bus_names_the_request_s_correlation_id(CapturedOutput output) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("not-a-token");
        headers.set("X-Correlation-Id", "log-corr-0001");

        ResponseEntity<String> response = http.postForEntity(url("/payments/cash-in"),
                new HttpEntity<>("{}", headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("log-corr-0001");
        assertThat(output.getAll())
                .containsPattern("\\[log-corr-0001\\] .*Token validation failed");
    }
}
