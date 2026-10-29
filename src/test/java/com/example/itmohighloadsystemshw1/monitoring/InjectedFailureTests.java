package com.example.itmohighloadsystemshw1.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static com.example.itmohighloadsystemshw1.monitoring.HttpMetricsTests.hasSample;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "FAILURE_RATE=1")
@AutoConfigureMockMvc
@AutoConfigureMetrics
class InjectedFailureTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void injectedFailureIsCountedAs500() throws Exception {
        mvc.perform(get("/items/{id}", 1)).andExpect(status().isInternalServerError());

        String metrics = mvc.perform(get("/metrics")).andReturn().getResponse().getContentAsString();

        assertThat(hasSample(metrics, "app_http_requests_total",
                "method=\"GET\"", "route=\"/items/{id}\"", "status=\"500\"")).isTrue();
    }
}
