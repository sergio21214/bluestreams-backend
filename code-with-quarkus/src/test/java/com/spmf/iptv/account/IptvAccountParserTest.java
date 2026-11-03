package com.spmf.iptv.account;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IptvAccountParserTest {

    private final IptvAccountParser parser = new IptvAccountParser();

    @Test
    void parsesUrlWithTrailingExpiryComment() {

        List<IptvAccountParser.ParsedAccount> result = parser.parse("""
                http://provider.example.com:80/get.php?username=user1&password=pass1&type=m3u_plus  # 2027-08-19 (faltan 298d)
                http://provider2.example.com:80/get.php?username=user2&password=pass2&type=m3u_plus&output=m3u8  # 2027-01-23 (faltan 121d)
                """);

        assertEquals(2, result.size());

        IptvAccountParser.ParsedAccount first = result.get(0);
        assertEquals(
                "http://provider.example.com:80/get.php?username=user1&password=pass1&type=m3u_plus",
                first.url()
        );
        assertEquals("provider.example.com", first.label());
        assertEquals("XTREAM", first.type());
        assertEquals(LocalDate.of(2027, 8, 19), first.expiresAt());

        assertEquals("XTREAM", result.get(1).type());
        assertEquals(LocalDate.of(2027, 1, 23), result.get(1).expiresAt());
    }

    @Test
    void attachesWrappedDateToPreviousUrl() {

        List<IptvAccountParser.ParsedAccount> result = parser.parse("""
                http://provider3.example.com:2095/get.php?username=user3&password=pass3&type=m3u_plus  #

                2027-04-21 (faltan 174d)
                """);

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2027, 4, 21), result.get(0).expiresAt());
    }

    @Test
    void detectsSpecializedTypes() {

        assertEquals(
                "XMLTV",
                IptvAccountParser.detectType(
                        "http://provider4.example.com/xmltv.php?username=user4&password=pass4"
                )
        );

        assertEquals(
                "M3U8",
                IptvAccountParser.detectType(
                        "http://example.com/live/list.m3u8"
                )
        );

        assertEquals(
                "TS",
                IptvAccountParser.detectType(
                        "http://example.com/live/123.ts"
                )
        );

        assertEquals(
                "M3U",
                IptvAccountParser.detectType(
                        "http://example.com/list.m3u"
                )
        );
    }

    @Test
    void ignoresUnlimitedExpiry() {

        List<IptvAccountParser.ParsedAccount> result = parser.parse("""
                http://provider5.example.com:80/get.php?username=user5&password=pass5&type=m3u_plus  # Sin límite
                """);

        assertEquals(1, result.size());
        assertNull(result.get(0).expiresAt());
    }

    @Test
    void handlesBlankInput() {

        assertEquals(0, parser.parse(null).size());
        assertEquals(0, parser.parse("   \n\n").size());
    }
}
