package com.spmf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.List;

public class FFprobeService {

    public static List<AudioTrack>
    getAudioTracks(String videoPath) {

        List<AudioTrack> tracks =
                new ArrayList<>();

        try {

            ProcessBuilder builder =
                    new ProcessBuilder(

                            "ffprobe",

                            "-v", "quiet",

                            "-print_format", "json",

                            "-show_streams",

                            videoPath
                    );

            Process process =
                    builder.start();

            BufferedReader reader =
                    new BufferedReader(

                            new InputStreamReader(
                                    process.getInputStream()
                            )
                    );

            StringBuilder json =
                    new StringBuilder();

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                json.append(line);
            }

            ObjectMapper mapper =
                    new ObjectMapper();

            JsonNode root =
                    mapper.readTree(
                            json.toString()
                    );

            JsonNode streams =
                    root.get("streams");

            for (JsonNode stream : streams) {

                if (
                        stream.get("codec_type")
                                .asText()
                                .equals("audio")
                ) {

                    AudioTrack track =
                            new AudioTrack();

                    track.index =
                            stream.get("index")
                                    .asInt();

                    JsonNode tags =
                            stream.get("tags");

                    if (
                            tags != null
                                    && tags.get("language")
                                    != null
                    ) {

                        track.language =
                                tags.get("language")
                                        .asText();

                    } else {

                        track.language =
                                "unknown";
                    }

                    tracks.add(track);
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

        return tracks;
    }

    public static List<SubtitleTrack> getSubtitleTracks(
            String inputPath
    ) {

        List<SubtitleTrack> tracks =
                new ArrayList<>();

        try {

            ProcessBuilder builder =
                    new ProcessBuilder(

                            "ffprobe",

                            "-v",
                            "quiet",

                            "-print_format",
                            "json",

                            "-show_streams",

                            inputPath
                    );

            Process process =
                    builder.start();

            String json =
                    new String(
                            process
                                    .getInputStream()
                                    .readAllBytes()
                    );

            ObjectMapper mapper =
                    new ObjectMapper();

            JsonNode root =
                    mapper.readTree(json);

            JsonNode streams =
                    root.get("streams");

            if (streams == null) {
                return tracks;
            }

            int subtitleIndex = 0;

            for (JsonNode stream : streams) {

                String codecType =
                        stream
                                .get("codec_type")
                                .asText();

                if (
                        codecType.equals(
                                "subtitle"
                        )
                ) {

                    SubtitleTrack track =
                            new SubtitleTrack();

                    track.index =
                            subtitleIndex;

                    track.language =
                            stream.has("tags")
                                    && stream.get("tags")
                                    .has("language")
                                    ? stream.get("tags")
                                    .get("language")
                                    .asText()
                                    : "unknown";

                    track.title =
                            stream.has("tags")
                                    && stream.get("tags")
                                    .has("title")
                                    ? stream.get("tags")
                                    .get("title")
                                    .asText()
                                    : "Subtitle";

                    tracks.add(track);

                    subtitleIndex++;
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

        return tracks;
    }
}