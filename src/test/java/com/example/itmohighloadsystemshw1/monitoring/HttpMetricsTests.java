package com.example.itmohighloadsystemshw1.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"POD_ID=pod-test", "DATACENTER=dc-test"})
@AutoConfigureMockMvc
@AutoConfigureMetrics
class HttpMetricsTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void listsOnlyAnnotatedHandlers() throws Exception {
        mvc.perform(get("/handlers"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"method": "POST", "route": "/items"},
                          {"method": "GET", "route": "/items/{id}"},
                          {"method": "PUT", "route": "/items/{id}"}
                        ]""", JsonCompareMode.STRICT));
    }

    @Test
    void exportsSignalsLabelledByRouteTemplateAndStatus() throws Exception {
        mvc.perform(post("/items").contentType(APPLICATION_JSON).content("""
                        {"name": "book"}"""))
                .andExpect(status().isCreated());
        mvc.perform(get("/items/{id}", 1_000_000_000)).andExpect(status().isNotFound());
        mvc.perform(get("/health")).andExpect(status().isOk());

        String metrics = scrape();

        assertThat(hasSample(metrics, "app_http_requests_total",
                "pod=\"pod-test\"", "datacenter=\"dc-test\"", "method=\"POST\"", "route=\"/items\"", "status=\"201\""))
                .as("201 of POST /items").isTrue();
        assertThat(hasSample(metrics, "app_http_requests_total",
                "method=\"GET\"", "route=\"/items/{id}\"", "status=\"404\""))
                .as("404 is labelled with the route template, not with the actual path").isTrue();
        assertThat(hasSample(metrics, "app_http_request_duration_seconds_bucket",
                "pod=\"pod-test\"", "datacenter=\"dc-test\"", "method=\"POST\"", "route=\"/items\"", "le=\"+Inf\""))
                .as("latency histogram buckets").isTrue();
        assertThat(hasSample(metrics, "app_http_request_duration_seconds_bucket",
                "method=\"PUT\"", "route=\"/items/{id}\""))
                .as("handlers without traffic are registered up front").isTrue();
        assertThat(metrics).doesNotContain("route=\"/health\"", "route=\"/metrics\"", "route=\"/handlers\"");
    }

    private String scrape() throws Exception {
        return mvc.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    static boolean hasSample(String exposition, String metric, String... labels) {
        return exposition.lines()
                .filter(line -> line.startsWith(metric + "{"))
                .anyMatch(line -> Arrays.stream(labels).allMatch(line::contains));
    }
}
