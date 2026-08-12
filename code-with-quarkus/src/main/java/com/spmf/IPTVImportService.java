package com.spmf;

import com.spmf.tv.IPTVChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class IPTVImportService {

    public void importFromUrl(String providerName, String url, String user, String pass) throws Exception {

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     new URL(url).openStream(),
                                     StandardCharsets.UTF_8))) {

            importPlaylist(providerName, url, user, pass, reader);
        }
    }

    @Transactional
    public void importFromStream(InputStream inputStream,
                                 String sourceName) throws Exception {

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            IPTVProvider provider =
                    IPTVProvider.find("name", sourceName)
                            .firstResult();

            if (provider == null) {

                provider = new IPTVProvider();
                provider.name = sourceName;
                provider.enabled = true;
                provider.persist();

            }

            parsePlaylist(reader, provider);

        }

    }

    /**
     * Small holder for a parsed #EXTINF entry, kept in memory until we know
     * which entries are new vs existing so we can batch DB access.
     */
    private static final class ParsedEntry {
        String name;
        String logo;
        String group;
        String streamUrl;
    }

    private void parsePlaylist(
            BufferedReader reader,
            IPTVProvider provider
    ) throws Exception {

        // ---- Pass 1: parse the whole file in memory (no DB access here) ----
        List<ParsedEntry> entries = new ArrayList<>();

        String line;
        String name = "";
        String logo = "";
        String group = "";

        while ((line = reader.readLine()) != null) {

            line = line.trim();

            if (line.isBlank()) {
                continue;
            }

            if (line.startsWith("#EXTINF")) {

                name = "";
                logo = "";
                group = "";

                int comma = line.lastIndexOf(',');
                if (comma >= 0) {
                    name = line.substring(comma + 1).trim();
                }

                logo = extract(line, "tvg-logo=\"", "\"");
                group = extract(line, "group-title=\"", "\"");

            } else if (!line.startsWith("#")) {

                ParsedEntry entry = new ParsedEntry();
                entry.name = name;
                entry.logo = logo;
                entry.group = group;
                entry.streamUrl = line;
                entries.add(entry);
            }
        }

        if (entries.isEmpty()) {
            return;
        }

        // ---- Pass 2: load all channels that already exist, in chunks ----
        // Postgres prepared statements cap out at 65,535 bind parameters, so a
        // single "streamUrl in (...)" for a huge playlist can blow past that.
        // Chunk the lookup to stay well under the limit.
        final int CHUNK_SIZE = 20_000;

        List<String> streamUrls = entries.stream()
                .map(e -> e.streamUrl)
                .toList();

        Map<String, IPTVChannel> existingByUrl = new HashMap<>();

        for (int i = 0; i < streamUrls.size(); i += CHUNK_SIZE) {

            List<String> chunk = streamUrls.subList(
                    i,
                    Math.min(i + CHUNK_SIZE, streamUrls.size())
            );

            List<IPTVChannel> existingChannels =
                    IPTVChannel.list("streamUrl in ?1", chunk);

            for (IPTVChannel c : existingChannels) {
                existingByUrl.put(c.streamUrl, c);
            }
        }

        // ---- Pass 3: diff in memory, no DB access here either ----
        // Guard against the same streamUrl appearing more than once in the
        // same playlist file (common with re-exported/aggregated M3Us) so we
        // don't try to insert the same new channel twice in one batch.
        List<IPTVChannel> toInsert = new ArrayList<>();
        Map<String, IPTVChannel> newInThisBatch = new HashMap<>();

        for (ParsedEntry entry : entries) {

            IPTVChannel existing = existingByUrl.get(entry.streamUrl);

            if (existing == null) {

                IPTVChannel pending = newInThisBatch.get(entry.streamUrl);

                if (pending != null) {
                    // Already queued for insert earlier in this same file;
                    // just update it with the latest values instead of
                    // creating a second row for the same streamUrl.
                    pending.name = entry.name;
                    pending.logo = entry.logo;
                    pending.groupName = entry.group;
                    continue;
                }

                IPTVChannel channel = new IPTVChannel();
                channel.name = entry.name;
                channel.logo = entry.logo;
                channel.groupName = entry.group;
                channel.streamUrl = entry.streamUrl;
                channel.providerId = provider.id;
                channel.providerName = provider.name;
                channel.enabled = true;

                toInsert.add(channel);
                newInThisBatch.put(entry.streamUrl, channel);

            } else {

                existing.name = entry.name;
                existing.logo = entry.logo;
                existing.groupName = entry.group;
                existing.providerId = provider.id;
                existing.providerName = provider.name;
                existing.enabled = true;
                // existing entity is managed, Hibernate will flush the update automatically
            }
        }

        // ---- Pass 4: one batched insert for everything new ----
        if (!toInsert.isEmpty()) {
            IPTVChannel.persist(toInsert);
        }
    }

    private String extract(
            String text,
            String begin,
            String end
    ) {

        int start = text.indexOf(begin);

        if (start < 0) {
            return "";
        }

        start += begin.length();

        int finish =
                text.indexOf(end, start);

        if (finish < 0) {
            return "";
        }

        return text.substring(start, finish);

    }

    @Transactional
    public void importFromText(
            String sourceName,
            String content
    ) throws Exception {

        importFromStream(

                new ByteArrayInputStream(
                        content.getBytes(
                                StandardCharsets.UTF_8
                        )
                ),

                sourceName

        );

    }

    @Transactional
    void importPlaylist(
            String providerName,
            String url,
            String user,
            String pass,
            BufferedReader reader) throws Exception {

        IPTVProvider provider =
                IPTVProvider.find("name", providerName).firstResult();

        if (provider == null) {
            provider = new IPTVProvider();
            provider.name = providerName;
            provider.url = url;
            provider.persist();
        } else {
            provider.url = url;
        }

        parsePlaylist(reader, provider);
    }

}