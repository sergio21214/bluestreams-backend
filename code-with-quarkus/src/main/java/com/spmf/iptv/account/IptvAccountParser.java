package com.spmf.iptv.account;

import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class IptvAccountParser {

    private static final Pattern URL_PATTERN =
            Pattern.compile("(?i)https?://\\S+");

    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");

    public record ParsedAccount(
            String url,
            String label,
            String type,
            LocalDate expiresAt
    ) {
    }

    public List<ParsedAccount> parse(String text) {

        List<ParsedAccount> result = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return result;
        }

        ParsedAccount last = null;

        for (String rawLine : text.split("\\R")) {

            String line = rawLine.trim();

            if (line.isBlank()) {
                continue;
            }

            Matcher urlMatcher = URL_PATTERN.matcher(line);

            if (urlMatcher.find()) {

                String url = cleanUrl(urlMatcher.group());

                if (url == null) {
                    continue;
                }

                String remainder = line.substring(urlMatcher.end());

                ParsedAccount account = new ParsedAccount(
                        url,
                        hostOf(url),
                        detectType(url),
                        findDate(remainder)
                );

                result.add(account);
                last = account;

            } else if (last != null) {

                LocalDate date = findDate(line);

                if (date != null) {
                    last = new ParsedAccount(
                            last.url(),
                            last.label(),
                            last.type(),
                            date
                    );
                    result.set(result.size() - 1, last);
                }
            }
        }

        return result;
    }

    private String cleanUrl(String value) {

        if (value == null) {
            return null;
        }

        int hash = value.indexOf('#');

        if (hash >= 0) {
            value = value.substring(0, hash);
        }

        value = value.replaceAll("[,;]+$", "").trim();

        return value.isBlank() ? null : value;
    }

    private LocalDate findDate(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        Matcher matcher = DATE_PATTERN.matcher(value);

        if (!matcher.find()) {
            return null;
        }

        try {
            return LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String hostOf(String url) {

        try {
            String host = URI.create(url).getHost();

            if (host == null || host.isBlank()) {
                return "unknown";
            }

            return host.replace("[", "").replace("]", "");
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    public static String detectType(String url) {

        String lower = url.toLowerCase(Locale.ROOT);

        if (lower.contains("xmltv.php")) {
            return "XMLTV";
        }

        String path = lower;

        try {
            URI uri = URI.create(url);
            path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
        }

        if (path.endsWith(".m3u8")) {
            return "M3U8";
        }

        if (path.endsWith(".m3u")) {
            return "M3U";
        }

        if (path.endsWith(".ts")) {
            return "TS";
        }

        if (path.contains("get.php") || lower.contains("type=m3u")) {
            return "XTREAM";
        }

        return "UNKNOWN";
    }
}
