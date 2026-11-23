package com.spmf.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.health.SmallRyeHealth;
import io.smallrye.health.SmallRyeHealthReporter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class SystemOverviewService {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SmallRyeHealthReporter healthReporter;

    @Inject
    SystemStatsService statsService;

    @ConfigProperty(
            name = "observability.prometheus.url",
            defaultValue = "http://prometheus:9090"
    )
    String prometheusUrl;

    @ConfigProperty(
            name = "observability.grafana.url"
    )
    Optional<String> grafanaUrl;

    @ConfigProperty(
            name = "observability.uptime-kuma.url"
    )
    Optional<String> uptimeKumaUrl;

    @ConfigProperty(
            name = "media.root",
            defaultValue = "/mnt/bluestreams/SPMF"
    )
    String mediaRoot;

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();

    public SystemOverview overview() {

        SystemStatsService.DbStats db =
                statsService.load();

        Map<String, Boolean> services =
                queryServices();

        boolean prometheusAvailable =
                services != null;

        SystemOverview.Metrics metrics =
                prometheusAvailable
                        ? metrics()
                        : emptyMetrics();

        List<SystemOverview.ServiceStatus> serviceStatuses =
                new ArrayList<>();

        if (services != null) {
            services.forEach((job, up) ->
                    serviceStatuses.add(
                            new SystemOverview.ServiceStatus(job, up)
                    )
            );
        }

        return new SystemOverview(
                Instant.now(),
                health(),
                metrics,
                serviceStatuses,
                db.music(),
                db.iptv(),
                disk(),
                links()
        );
    }

    private SystemOverview.Health health() {

        try {

            SmallRyeHealth health =
                    healthReporter.getHealth();

            List<SystemOverview.HealthCheck> checks =
                    new ArrayList<>();

            JsonObject payload =
                    health.getPayload();

            if (payload != null) {

                JsonArray array =
                        payload.getJsonArray("checks");

                if (array != null) {

                    for (JsonValue value : array) {

                        if (value instanceof JsonObject check) {

                            checks.add(
                                    new SystemOverview.HealthCheck(
                                            check.getString("name", "check"),
                                            check.getString("status", "UNKNOWN")
                                    )
                            );
                        }
                    }
                }
            }

            return new SystemOverview.Health(
                    health.isDown() ? "DOWN" : "UP",
                    checks
            );

        } catch (Exception e) {

            return new SystemOverview.Health(
                    "UNKNOWN",
                    List.of()
            );
        }
    }

    private SystemOverview.Metrics metrics() {

        Double requests =
                queryScalar(
                        "sum(rate(http_server_requests_seconds_count[5m]))"
                );

        Double errors =
                queryScalar(
                        "sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[5m]))"
                );

        Double errorRate =
                requests != null
                        && requests > 0
                        && errors != null
                        ? (errors / requests) * 100.0
                        : 0.0;

        return new SystemOverview.Metrics(
                true,
                requests,
                errors,
                errorRate,
                queryScalar(
                        "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le)) * 1000"
                ),
                queryScalar(
                        "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le)) * 1000"
                ),
                queryScalar(
                        "sum(jvm_memory_used_bytes{area=\"heap\"})"
                ),
                queryScalar(
                        "sum(jvm_memory_max_bytes{area=\"heap\"})"
                ),
                queryScalar("process_cpu_usage"),
                queryScalar("system_cpu_usage")
        );
    }

    private SystemOverview.Metrics emptyMetrics() {

        return new SystemOverview.Metrics(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Map<String, Boolean> queryServices() {

        try {

            JsonNode root =
                    query("up");

            if (root == null) {
                return null;
            }

            JsonNode result =
                    root.path("data").path("result");

            if (!result.isArray()) {
                return null;
            }

            Map<String, Boolean> services =
                    new LinkedHashMap<>();

            for (JsonNode series : result) {

                String job =
                        series.path("metric").path("job").asText("");

                if (job.isBlank()) {
                    continue;
                }

                boolean up =
                        "1".equals(
                                series.path("value")
                                        .path(1)
                                        .asText()
                        );

                services.merge(job, up, (a, b) -> a && b);
            }

            return services;

        } catch (Exception e) {

            return null;
        }
    }

    private Double queryScalar(String promql) {

        try {

            JsonNode root =
                    query(promql);

            if (root == null) {
                return null;
            }

            JsonNode result =
                    root.path("data").path("result");

            if (!result.isArray() || result.isEmpty()) {
                return null;
            }

            String value =
                    result.get(0)
                            .path("value")
                            .path(1)
                            .asText();

            double parsed =
                    Double.parseDouble(value);

            return Double.isFinite(parsed)
                    ? parsed
                    : null;

        } catch (Exception e) {

            return null;
        }
    }

    private JsonNode query(String promql) {

        try {

            if (
                    prometheusUrl == null
                            || prometheusUrl.isBlank()
            ) {
                return null;
            }

            String base =
                    prometheusUrl.endsWith("/")
                            ? prometheusUrl.substring(
                                    0,
                                    prometheusUrl.length() - 1
                            )
                            : prometheusUrl;

            String url =
                    base
                            + "/api/v1/query?query="
                            + URLEncoder.encode(
                                    promql,
                                    StandardCharsets.UTF_8
                            );

            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(3))
                            .header("Accept", "application/json")
                            .GET()
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() != 200) {
                return null;
            }

            JsonNode root =
                    objectMapper.readTree(response.body());

            if (
                    !"success".equals(
                            root.path("status").asText()
                    )
            ) {
                return null;
            }

            return root;

        } catch (Exception e) {

            return null;
        }
    }

    private SystemOverview.DiskStats disk() {

        try {

            FileStore store =
                    Files.getFileStore(
                            Path.of(mediaRoot)
                    );

            long total =
                    store.getTotalSpace();

            long usable =
                    store.getUsableSpace();

            double used =
                    total > 0
                            ? (1.0 - ((double) usable / total)) * 100.0
                            : 0.0;

            return new SystemOverview.DiskStats(
                    total,
                    usable,
                    used
            );

        } catch (Exception e) {

            return new SystemOverview.DiskStats(
                    null,
                    null,
                    null
            );
        }
    }

    private SystemOverview.Links links() {

        return new SystemOverview.Links(
                blankToNull(grafanaUrl.orElse(null)),
                blankToNull(uptimeKumaUrl.orElse(null))
        );
    }

    private String blankToNull(String value) {

        return value == null || value.isBlank()
                ? null
                : value;
    }
}
