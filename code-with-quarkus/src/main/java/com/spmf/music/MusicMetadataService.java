package com.spmf.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class MusicMetadataService {

    private static final String FFPROBE =
            "/usr/bin/ffprobe";

    private static final String ARTWORK_FOLDER =
            "/mnt/bluestreams/SPMF/media/music/music-art";

    @Inject
    ObjectMapper objectMapper;

    public MusicMetadata read(File file) {

        MusicMetadata result =
                new MusicMetadata();

        boolean webmOrOpus =
                isWebmOrOpus(file);

        /*
         * WebM/Opus is not reliably supported by Jaudiotagger,
         * so use ffprobe directly.
         */
        if (webmOrOpus) {

            readWithFfprobe(file, result);

            return result;
        }

        /*
         * Existing behavior for MP3, FLAC, M4A, etc.
         */
        try {

            AudioFile audio =
                    AudioFileIO.read(file);

            Tag tag =
                    audio.getTag();

            if (tag != null) {

                result.title =
                        clean(tag.getFirst(FieldKey.TITLE));

                result.artist =
                        clean(tag.getFirst(FieldKey.ARTIST));

                result.album =
                        clean(tag.getFirst(FieldKey.ALBUM));

                try {

                    String track =
                            tag.getFirst(FieldKey.TRACK);

                    if (
                            track != null &&
                                    !track.isBlank()
                    ) {

                        result.trackNumber =
                                Integer.parseInt(
                                        track.split("/")[0]
                                );
                    }

                } catch (Exception ignored) {
                }
            }

            result.durationMs =
                    audio.getAudioHeader()
                            .getTrackLength()
                            * 1000L;

        } catch (Exception e) {

            System.out.println(
                    "Jaudiotagger failed for "
                            + file.getName()
                            + ", trying ffprobe..."
            );

            readWithFfprobe(file, result);
        }

        /*
         * Even when Jaudiotagger succeeds, use ffprobe as a
         * fallback for individual missing fields.
         */
        if (
                isBlank(result.title) ||
                        isBlank(result.artist) ||
                        isBlank(result.album) ||
                        result.durationMs == null
        ) {

            MusicMetadata ffprobeMetadata =
                    new MusicMetadata();

            readWithFfprobe(
                    file,
                    ffprobeMetadata
            );

            if (
                    isBlank(result.title) &&
                            !isBlank(ffprobeMetadata.title)
            ) {

                result.title =
                        ffprobeMetadata.title;
            }

            if (
                    isBlank(result.artist) &&
                            !isBlank(ffprobeMetadata.artist)
            ) {

                result.artist =
                        ffprobeMetadata.artist;
            }

            if (
                    isBlank(result.album) &&
                            !isBlank(ffprobeMetadata.album)
            ) {

                result.album =
                        ffprobeMetadata.album;
            }

            if (
                    result.durationMs == null &&
                            ffprobeMetadata.durationMs != null
            ) {

                result.durationMs =
                        ffprobeMetadata.durationMs;
            }
        }

        return result;
    }

    private void readWithFfprobe(
            File file,
            MusicMetadata result
    ) {

        try {

            List<String> command =
                    new ArrayList<>();

            command.add(FFPROBE);

            command.add("-v");
            command.add("quiet");

            command.add("-show_entries");
            command.add(
                    "format_tags:format=duration"
            );

            command.add("-of");
            command.add("json");

            command.add(
                    file.getAbsolutePath()
            );

            ProcessBuilder builder =
                    new ProcessBuilder(command);

            Process process =
                    builder.start();

            StringBuilder output =
                    new StringBuilder();

            try (
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            process.getInputStream(),
                                            StandardCharsets.UTF_8
                                    )
                            )
            ) {

                String line;

                while (
                        (line = reader.readLine())
                                != null
                ) {

                    output.append(line);
                }
            }

            int exitCode =
                    process.waitFor();

            if (exitCode != 0) {

                System.out.println(
                        "ffprobe failed for "
                                + file.getName()
                );

                return;
            }

            JsonNode root =
                    objectMapper.readTree(
                            output.toString()
                    );

            JsonNode format =
                    root.get("format");

            if (format == null) {
                return;
            }

            JsonNode tags =
                    format.get("tags");

            if (tags != null) {

                result.title =
                        firstTag(
                                tags,
                                "title"
                        );

                result.artist =
                        firstTag(
                                tags,
                                "ARTIST"
                        );

                result.album =
                        firstTag(
                                tags,
                                "ALBUM"
                        );

                if (
                        isBlank(result.artist)
                ) {

                    result.artist =
                            firstTag(
                                    tags,
                                    "artist"
                            );
                }

                if (
                        isBlank(result.album)
                ) {

                    result.album =
                            firstTag(
                                    tags,
                                    "album"
                            );
                }

                String track =
                        firstTag(
                                tags,
                                "TRACKNUMBER"
                        );

                if (
                        !isBlank(track)
                ) {

                    try {

                        result.trackNumber =
                                Integer.parseInt(
                                        track.split("/")[0]
                                );

                    } catch (Exception ignored) {
                    }
                }
            }

            JsonNode duration =
                    format.get("duration");

            if (
                    duration != null &&
                            !duration.isNull()
            ) {

                result.durationMs =
                        Math.round(
                                duration.asDouble()
                                        * 1000.0
                        );
            }

            System.out.println(
                    "ffprobe metadata:"
            );

            System.out.println(
                    "  title  = "
                            + result.title
            );

            System.out.println(
                    "  artist = "
                            + result.artist
            );

            System.out.println(
                    "  album  = "
                            + result.album
            );

            System.out.println(
                    "  durationMs = "
                            + result.durationMs
            );

        } catch (Exception e) {

            System.out.println(
                    "Error reading ffprobe metadata for "
                            + file.getName()
            );

            e.printStackTrace();
        }
    }

    private String firstTag(
            JsonNode tags,
            String name
    ) {

        JsonNode value =
                tags.get(name);

        if (
                value == null ||
                        value.isNull()
        ) {

            return null;
        }

        return clean(
                value.asText()
        );
    }

    private boolean isWebmOrOpus(
            File file
    ) {

        String name =
                file.getName()
                        .toLowerCase();

        return name.endsWith(".webm")
                || name.endsWith(".opus");
    }

    private String clean(
            String value
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {

            return null;
        }

        return value.trim();
    }

    private boolean isBlank(
            String value
    ) {

        return value == null ||
                value.isBlank();
    }

    public String extractArtwork(
            File musicFile,
            Long mediaId
    ) {

        /*
         * WebM/Opus artwork is handled separately later.
         * Jaudiotagger cannot reliably extract it here.
         */
        if (isWebmOrOpus(musicFile)) {
            return null;
        }

        try {

            AudioFile audio =
                    AudioFileIO.read(
                            musicFile
                    );

            Tag tag =
                    audio.getTag();

            if (tag == null) {
                return null;
            }

            if (
                    tag.getFirstArtwork()
                            == null
            ) {

                return null;
            }

            byte[] image =
                    tag.getFirstArtwork()
                            .getBinaryData();

            Files.createDirectories(
                    Path.of(ARTWORK_FOLDER)
            );

            String output =
                    ARTWORK_FOLDER
                            + "/"
                            + mediaId
                            + ".jpg";

            Files.write(
                    Path.of(output),
                    image
            );

            return "/music-art/"
                    + mediaId
                    + ".jpg";

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }
    }
}