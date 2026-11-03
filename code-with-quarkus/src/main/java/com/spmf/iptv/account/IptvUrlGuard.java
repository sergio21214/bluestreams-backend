package com.spmf.iptv.account;

import okhttp3.HttpUrl;

import java.net.InetAddress;
import java.util.Locale;

/**
 * SSRF guard for outbound IPTV checks.
 *
 * <p>Only absolute http/https URLs that resolve exclusively to public
 * addresses are allowed. Loopback, private, link-local, CGNAT, multicast,
 * documentation and other reserved ranges are rejected, including their
 * IPv4-mapped IPv6 forms.</p>
 */
public final class IptvUrlGuard {

    private IptvUrlGuard() {
    }

    public static void validate(HttpUrl url) throws BlockedUrlException {

        if (url == null) {
            throw new BlockedUrlException("invalid URL");
        }

        String scheme = url.scheme().toLowerCase(Locale.ROOT);

        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BlockedUrlException("scheme not allowed: " + scheme);
        }

        String host = url.host();

        if (host == null || host.isBlank()) {
            throw new BlockedUrlException("missing host");
        }

        if (!url.username().isEmpty() || !url.password().isEmpty()) {
            throw new BlockedUrlException("credentials in URL are not allowed");
        }

        String normalizedHost = host.replace("[", "").replace("]", "")
                .toLowerCase(Locale.ROOT);

        if (normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")
                || normalizedHost.endsWith(".internal")
                || normalizedHost.endsWith(".home.arpa")
                || normalizedHost.equals("metadata.google.internal")) {
            throw new BlockedUrlException("host not allowed: " + normalizedHost);
        }

        if (isIpLiteral(normalizedHost)) {
            try {
                InetAddress address = InetAddress.getByName(normalizedHost);
                if (isBlocked(address)) {
                    throw new BlockedUrlException("non-public address: " + normalizedHost);
                }
            } catch (BlockedUrlException e) {
                throw e;
            } catch (Exception e) {
                throw new BlockedUrlException("invalid IP literal: " + normalizedHost);
            }
        }
    }

    public static boolean isIpLiteral(String host) {

        if (host == null) {
            return false;
        }

        if (host.contains(":")) {
            return true;
        }

        return host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$");
    }

    public static boolean isBlocked(InetAddress address) {

        if (address == null) {
            return true;
        }

        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();

        if (bytes.length == 4) {
            return isBlockedIpv4(bytes);
        }

        if (bytes.length == 16) {
            return isBlockedIpv6(bytes);
        }

        return true;
    }

    private static boolean isBlockedIpv4(byte[] bytes) {

        int a = bytes[0] & 0xff;
        int b = bytes[1] & 0xff;
        int c = bytes[2] & 0xff;
        int d = bytes[3] & 0xff;

        if (a == 0) {
            return true;
        }

        if (a == 10) {
            return true;
        }

        if (a == 100 && b >= 64 && b <= 127) {
            return true;
        }

        if (a == 127) {
            return true;
        }

        if (a == 169 && b == 254) {
            return true;
        }

        if (a == 172 && b >= 16 && b <= 31) {
            return true;
        }

        if (a == 192 && b == 0 && (c == 0 || c == 2)) {
            return true;
        }

        if (a == 192 && b == 88 && c == 99) {
            return true;
        }

        if (a == 192 && b == 168) {
            return true;
        }

        if (a == 198 && (b == 18 || b == 19)) {
            return true;
        }

        if (a == 198 && b == 51 && c == 100) {
            return true;
        }

        if (a == 203 && b == 0 && c == 113) {
            return true;
        }

        if (a >= 224) {
            return true;
        }

        return d == 255 && a == 255 && b == 255 && c == 255;
    }

    private static boolean isBlockedIpv6(byte[] bytes) {

        if (isIpv4Mapped(bytes)) {
            return isBlockedIpv4(new byte[]{
                    bytes[12], bytes[13], bytes[14], bytes[15]
            });
        }

        boolean firstTwelveZero = true;

        for (int i = 0; i < 12; i++) {
            if (bytes[i] != 0) {
                firstTwelveZero = false;
                break;
            }
        }

        if (firstTwelveZero) {
            return true;
        }

        int b0 = bytes[0] & 0xff;
        int b1 = bytes[1] & 0xff;
        int b2 = bytes[2] & 0xff;
        int b3 = bytes[3] & 0xff;

        if ((b0 & 0xfe) == 0xfc) {
            return true;
        }

        if (b0 == 0x20 && b1 == 0x01 && b2 == 0x0d && b3 == 0xb8) {
            return true;
        }

        if (b0 == 0x20 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00) {
            return true;
        }

        if (b0 == 0x20 && b1 == 0x01 && b2 == 0x00 && b3 == 0x02) {
            return true;
        }

        if (b0 == 0x00 && b1 == 0x64 && b2 == 0xff && b3 == 0x9b) {
            return true;
        }

        if (b0 == 0x20 && b1 == 0x02) {
            return true;
        }

        if (b0 == 0x01 && b1 == 0x00 && b2 == 0x00 && b3 == 0x00) {
            return true;
        }

        return false;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {

        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }

        return (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
    }

    public static String redact(HttpUrl url) {

        if (url == null) {
            return null;
        }

        return url.newBuilder()
                .username("")
                .password("")
                .query(null)
                .fragment(null)
                .build()
                .toString();
    }

    public static String redact(String rawUrl) {

        if (rawUrl == null) {
            return null;
        }

        HttpUrl parsed = HttpUrl.parse(rawUrl);

        if (parsed == null) {
            return "[invalid-url]";
        }

        return redact(parsed);
    }

    public static String sanitizeError(String message) {

        if (message == null) {
            return null;
        }

        String cleaned = message.replaceAll("(?i)https?://\\S+", "[url]");

        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    public static final class BlockedUrlException extends Exception {

        public BlockedUrlException(String message) {
            super(message);
        }
    }
}
