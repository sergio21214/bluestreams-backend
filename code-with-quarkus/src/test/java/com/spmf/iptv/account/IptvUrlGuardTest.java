package com.spmf.iptv.account;

import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IptvUrlGuardTest {

    @Test
    void blocksPrivateAndReservedRanges() throws Exception {

        assertTrue(IptvUrlGuard.isBlocked(InetAddress.getByName("127.0.0.1")));
        assertTrue(IptvUrlGuard.isBlocked(InetAddress.getByName("10.0.0.1")));
        assertTrue(IptvUrlGuard.isBlocked(InetAddress.getByName("172.16.0.1")));
        assertTrue(IptvUrlGuard.isBlocked(InetAddress.getByName("192.168.1.1")));
        assertTrue(IptvUrlGuard.isBlocked(InetAddress.getByName("169.254.169.254")));
        assertTrue(IptvUrlGuard.isBlocked(InetAddress.getByName("100.64.0.1")));
        assertTrue(IptvUrlGuard.isBlocked(InetAddress.getByName("0.0.0.0")));
        assertTrue(IptvUrlGuard.isBlocked(InetAddress.getByName("::1")));
        assertTrue(IptvUrlGuard.isBlocked(InetAddress.getByName("fd00::1")));
        assertTrue(IptvUrlGuard.isBlocked(InetAddress.getByName("fe80::1")));
        assertTrue(IptvUrlGuard.isBlocked(InetAddress.getByName("::ffff:127.0.0.1")));

        assertFalse(IptvUrlGuard.isBlocked(InetAddress.getByName("8.8.8.8")));
        assertFalse(IptvUrlGuard.isBlocked(InetAddress.getByName("1.1.1.1")));
        assertFalse(IptvUrlGuard.isBlocked(InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    void rejectsLiteralPrivateHost() {

        HttpUrl url = HttpUrl.parse("http://192.168.1.10/get.php");

        assertThrows(
                IptvUrlGuard.BlockedUrlException.class,
                () -> IptvUrlGuard.validate(url)
        );
    }

    @Test
    void rejectsNonHttpScheme() {

        HttpUrl url = HttpUrl.parse("ftp://example.com/file");

        assertThrows(
                IptvUrlGuard.BlockedUrlException.class,
                () -> IptvUrlGuard.validate(url)
        );
    }

    @Test
    void rejectsLocalhostName() {

        HttpUrl url = HttpUrl.parse("http://localhost:8082/music/home");

        assertThrows(
                IptvUrlGuard.BlockedUrlException.class,
                () -> IptvUrlGuard.validate(url)
        );
    }

    @Test
    void acceptsPublicHostname() throws Exception {

        IptvUrlGuard.validate(HttpUrl.parse("http://example.com/list.m3u"));
        IptvUrlGuard.validate(HttpUrl.parse("https://example.com/list.m3u"));
    }

    @Test
    void redactsQueryAndCredentials() {

        HttpUrl url = HttpUrl.parse(
                "http://user:pass@example.com/get.php?username=a&password=b"
        );

        String redacted = IptvUrlGuard.redact(url);

        assertFalse(redacted.contains("username"));
        assertFalse(redacted.contains("password"));
        assertFalse(redacted.contains("user:pass"));
        assertTrue(redacted.startsWith("http://example.com/get.php"));
    }
}
