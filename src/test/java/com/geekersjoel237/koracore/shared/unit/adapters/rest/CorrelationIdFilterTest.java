package com.geekersjoel237.koracore.shared.unit.adapters.rest;

import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;
import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    private HttpServletRequest passedOn() {
        return (HttpServletRequest) chain.getRequest();
    }

    @Test
    void a_command_keeps_the_correlation_id_it_was_sent_and_answers_with_it() throws Exception {
        var request = new MockHttpServletRequest("POST", "/payments/cash-in");
        request.addHeader(CorrelationId.HEADER, " corr-001 ");
        request.addHeader("Authorization", "Bearer token");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("corr-001");
        assertThat(passedOn().getHeader(CorrelationId.HEADER)).isEqualTo("corr-001");
        assertThat(Collections.list(passedOn().getHeaders(CorrelationId.HEADER))).containsExactly("corr-001");
        assertThat(passedOn().getHeader("Authorization")).isEqualTo("Bearer token");
        assertThat(passedOn().getAttribute(CorrelationId.ATTRIBUTE)).isEqualTo("corr-001");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void a_command_sent_with_a_blank_one_is_given_a_new_one(String blank) throws Exception {
        var request = new MockHttpServletRequest("POST", "/payments/cash-in");
        request.addHeader(CorrelationId.HEADER, blank);

        filter.doFilter(request, response, chain);

        String given = response.getHeader(CorrelationId.HEADER);
        assertThat(given).isNotBlank();
        assertThat(passedOn().getHeader(CorrelationId.HEADER)).isEqualTo(given);
    }

    @Test
    void a_command_sent_without_one_is_given_one() throws Exception {
        filter.doFilter(new MockHttpServletRequest("POST", "/payments/transfer"), response, chain);

        assertThat(response.getHeader(CorrelationId.HEADER)).isNotBlank();
    }

    @Test
    void a_read_sent_without_one_is_left_without_one() throws Exception {
        var request = new MockHttpServletRequest("GET", "/payments/balance");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationId.HEADER)).isNull();
        assertThat(passedOn()).isSameAs(request);
    }

    @Test
    void every_line_logged_during_the_request_can_name_its_correlation_id_and_no_later_one_does() throws Exception {
        var request = new MockHttpServletRequest("POST", "/payments/cash-in");
        request.addHeader(CorrelationId.HEADER, "corr-002");
        List<String> seenDuringTheRequest = new ArrayList<>();

        filter.doFilter(request, response, (req, res) -> seenDuringTheRequest.add(MDC.get(CorrelationId.MDC_KEY)));

        assertThat(seenDuringTheRequest).containsExactly("corr-002");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }
}
