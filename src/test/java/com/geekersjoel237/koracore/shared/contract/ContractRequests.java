package com.geekersjoel237.koracore.shared.contract;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

public final class ContractRequests {

    public static final String CORRELATION_ID = "33333333-3333-3333-3333-333333333333";

    private ContractRequests() {
    }

    public static MvcTestResult post(MockMvcTester mvc, String path, String token, String body) {
        var request = mvc.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", CORRELATION_ID)
                .content(body);
        if (token != null)
            request = request.header("Authorization", "Bearer " + token);
        return request.exchange();
    }

    public static MvcTestResult get(MockMvcTester mvc, String pathAndQuery, String token) {
        var request = mvc.get().uri(pathAndQuery);
        if (token != null)
            request = request.header("Authorization", "Bearer " + token);
        return request.exchange();
    }
}
