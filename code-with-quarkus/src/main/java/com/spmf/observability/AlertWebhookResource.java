package com.spmf.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@Path("/internal/alerts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AlertWebhookResource {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AlertInboxService alertInboxService;

    @ConfigProperty(name = "alerts.webhook.token")
    Optional<String> webhookToken;

    @POST
    @Path("/alertmanager")
    public Response alertmanager(
            String body,
            @HeaderParam("Authorization") String authorization,
            @QueryParam("token") String queryToken
    ) {

        if (!authorized(authorization, queryToken)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        try {

            JsonNode root = objectMapper.readTree(body);
            JsonNode alerts = root.path("alerts");
            String groupStatus = root.path("status").asText("firing");

            if (!alerts.isArray()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "missing alerts"))
                        .build();
            }

            int recorded = 0;

            for (JsonNode alert : alerts) {

                String fingerprint =
                        alert.path("fingerprint").asText("");

                String name =
                        alert.path("labels")
                                .path("alertname")
                                .asText("alert");

                String status =
                        alert.path("status").asText(groupStatus);

                String severity =
                        alert.path("labels")
                                .path("severity")
                                .asText("warning");

                String summary =
                        alert.path("annotations")
                                .path("summary")
                                .asText("");

                String description =
                        alert.path("annotations")
                                .path("description")
                                .asText("");

                String labels =
                        alert.path("labels").toString();

                alertInboxService.record(
                        new AlertInboxService.AlertRecord(
                                fingerprint,
                                name,
                                status,
                                severity,
                                "prometheus",
                                summary,
                                description,
                                parseInstant(
                                        alert.path("startsAt").asText("")
                                ),
                                parseInstant(
                                        alert.path("endsAt").asText("")
                                ),
                                labels
                        )
                );

                recorded++;
            }

            return Response.ok(Map.of("recorded", recorded)).build();

        } catch (Exception e) {

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "invalid payload"))
                    .build();
        }
    }

    @POST
    @Path("/uptime")
    public Response uptime(
            String body,
            @HeaderParam("Authorization") String authorization,
            @QueryParam("token") String queryToken
    ) {

        if (!authorized(authorization, queryToken)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        try {

            JsonNode root = objectMapper.readTree(body);

            JsonNode monitor =
                    objectMapper.readTree(
                            asJson(root.path("monitor"))
                    );

            JsonNode heartbeat =
                    objectMapper.readTree(
                            asJson(root.path("heartbeat"))
                    );

            int status =
                    heartbeat.path("status").asInt(0);

            if (status != 0 && status != 1) {
                return Response.ok(Map.of("ignored", true)).build();
            }

            int monitorId =
                    heartbeat.path("monitorID")
                            .asInt(monitor.path("id").asInt(0));

            String name =
                    monitor.path("name").asText("uptime monitor");

            String message =
                    heartbeat.path("msg")
                            .asText(root.path("msg").asText(""));

            boolean up = status == 1;

            alertInboxService.record(
                    new AlertInboxService.AlertRecord(
                            "uptime-" + monitorId,
                            name,
                            up ? "resolved" : "firing",
                            up ? "info" : "critical",
                            "uptime-kuma",
                            "Monitor " + name + (up ? " is up" : " is down"),
                            message,
                            parseInstant(
                                    heartbeat.path("time").asText("")
                            ),
                            up ? Instant.now() : null,
                            monitor.toString()
                    )
            );

            return Response.ok(Map.of("recorded", 1)).build();

        } catch (Exception e) {

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "invalid payload"))
                    .build();
        }
    }

    private boolean authorized(
            String authorization,
            String queryToken
    ) {

        String expected =
                webhookToken.orElse("").trim();

        if (expected.isEmpty()) {
            return false;
        }

        String provided = null;

        if (
                authorization != null &&
                        authorization.startsWith("Bearer ")
        ) {
            provided = authorization.substring(7).trim();
        } else if (queryToken != null && !queryToken.isBlank()) {
            provided = queryToken.trim();
        }

        if (provided == null) {
            return false;
        }

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String asJson(JsonNode node) {

        if (node == null || node.isMissingNode() || node.isNull()) {
            return "{}";
        }

        if (node.isTextual()) {
            return node.asText();
        }

        return node.toString();
    }

    private Instant parseInstant(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {

            Instant instant = Instant.parse(value);

            return instant.getEpochSecond() <= 0
                    ? null
                    : instant;

        } catch (Exception ignored) {
        }

        try {

            return OffsetDateTime.parse(value).toInstant();

        } catch (Exception ignored) {
        }

        return null;
    }
}
