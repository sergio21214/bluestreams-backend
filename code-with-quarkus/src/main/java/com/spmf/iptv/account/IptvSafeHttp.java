package com.spmf.iptv.account;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import okhttp3.ConnectionSpec;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class IptvSafeHttp {

    @ConfigProperty(name = "iptv.liveness.connect-timeout-seconds", defaultValue = "5")
    int connectTimeoutSeconds;

    @ConfigProperty(name = "iptv.liveness.read-timeout-seconds", defaultValue = "10")
    int readTimeoutSeconds;

    @ConfigProperty(name = "iptv.liveness.call-timeout-seconds", defaultValue = "20")
    int callTimeoutSeconds;

    @ConfigProperty(name = "iptv.liveness.max-bytes", defaultValue = "65536")
    int maxBytes;

    @ConfigProperty(name = "iptv.liveness.max-redirects", defaultValue = "3")
    int maxRedirects;

    @ConfigProperty(name = "iptv.liveness.user-agent", defaultValue = "VLC/3.0.20 LibVLC/3.0.20")
    String userAgent;

    private OkHttpClient client;

    @PostConstruct
    void init() {

        this.client = new OkHttpClient.Builder()
                .dns(new PublicOnlyDns())
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
                .connectionSpecs(List.of(
                        ConnectionSpec.MODERN_TLS,
                        ConnectionSpec.CLEARTEXT
                ))
                .build();
    }

    public FetchResult fetch(String rawUrl)
            throws IptvUrlGuard.BlockedUrlException {

        HttpUrl url = HttpUrl.parse(rawUrl);

        if (url == null) {
            throw new IptvUrlGuard.BlockedUrlException("invalid URL");
        }

        int limit = Math.max(1024, maxBytes);

        for (int redirect = 0; redirect <= maxRedirects; redirect++) {

            IptvUrlGuard.validate(url);

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Range", "bytes=0-" + (limit - 1))
                    .get()
                    .build();

            long started = System.nanoTime();

            try (Response response = client.newCall(request).execute()) {

                int code = response.code();
                String contentType = response.header("Content-Type", "");
                long latencyMs = (System.nanoTime() - started) / 1_000_000L;

                if (code >= 300 && code < 400) {

                    String location = response.header("Location");

                    if (location == null || location.isBlank()) {
                        return new FetchResult(
                                code,
                                contentType,
                                new byte[0],
                                latencyMs,
                                "redirect without Location"
                        );
                    }

                    HttpUrl next = url.resolve(location);

                    if (next == null) {
                        return new FetchResult(
                                code,
                                contentType,
                                new byte[0],
                                latencyMs,
                                "invalid redirect Location"
                        );
                    }

                    url = next;
                    continue;
                }

                byte[] prefix = readPrefix(response.body(), limit);

                return new FetchResult(
                        code,
                        contentType == null ? "" : contentType,
                        prefix,
                        latencyMs,
                        null
                );

            } catch (IOException e) {
                long latencyMs = (System.nanoTime() - started) / 1_000_000L;

                return new FetchResult(
                        -1,
                        "",
                        new byte[0],
                        latencyMs,
                        IptvUrlGuard.sanitizeError(
                                e.getClass().getSimpleName() + ": " + e.getMessage()
                        )
                );
            }
        }

        return new FetchResult(
                -1,
                "",
                new byte[0],
                0L,
                "too many redirects"
        );
    }

    private byte[] readPrefix(ResponseBody body, int limit) throws IOException {

        if (body == null) {
            return new byte[0];
        }

        try (InputStream input = body.byteStream()) {
            return input.readNBytes(limit);
        }
    }

    public record FetchResult(
            int statusCode,
            String contentType,
            byte[] prefix,
            long latencyMs,
            String error
    ) {
    }

    /**
     * Resolves a host and drops every non-public address. OkHttp reuses this
     * validated list for the actual connection, which prevents DNS rebinding
     * from redirecting a check to an internal address.
     */
    private static final class PublicOnlyDns implements Dns {

        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {

            List<InetAddress> resolved = Dns.SYSTEM.lookup(hostname);

            if (resolved == null || resolved.isEmpty()) {
                throw new UnknownHostException("no addresses for " + hostname);
            }

            List<InetAddress> safe = new ArrayList<>(resolved.size());

            for (InetAddress address : resolved) {
                if (!IptvUrlGuard.isBlocked(address)) {
                    safe.add(address);
                }
            }

            if (safe.isEmpty()) {
                throw new UnknownHostException("blocked host: " + hostname);
            }

            return safe;
        }
    }

    public static String normalizeContentType(String contentType) {

        return contentType == null
                ? ""
                : contentType.toLowerCase(Locale.ROOT);
    }
}
