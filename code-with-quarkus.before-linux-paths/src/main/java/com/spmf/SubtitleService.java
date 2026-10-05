package com.spmf;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SubtitleService {

    public static void extractSubtitles(
            String inputPath,
            String videoFilename
    ) {

        try {

            System.out.println(
                    "SUBTITLE SERVICE START"
            );

            String movieName =
                    FilenameUtils.cleanName(
                            videoFilename
                    );

            File outputDir =
                    new File(
                            "G:/SPMF/subtitles/" + movieName
                    );

            outputDir.mkdirs();

//            String inputPath =
//                    "../../videos/" + videoFilename;

            List<SubtitleTrack> subtitles =
                    FFprobeService.getSubtitleTracks(
                            inputPath
                    );

            int index = 0;

            for (SubtitleTrack track : subtitles) {

                // LANGUAGE
                String lang =
                        normalizeLanguage(
                                track.language
                        );

                // LABEL
                String label =
                        normalizeLabel(
                                lang
                        );

                // FILE NAME
                String outputFilename =
                        "subtitle_"
                                + index
                                + "_"
                                + lang
                                + ".vtt";

                String outputPath =
                        "G:/SPMF/subtitles/"
                                + movieName
                                + "/"
                                + outputFilename;

                System.out.println(
                        "EXTRACTING SUBTITLE: "
                                + outputFilename
                );

                ProcessBuilder builder =
                        new ProcessBuilder(

                                "ffmpeg",

                                "-y",

                                "-i",
                                inputPath,

                                "-map",
                                "0:s:" + index + "?",

                                "-c:s",
                                "webvtt",

                                outputPath
                        );

                builder.redirectErrorStream(true);

                Process process =
                        builder.start();

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream()
                                )
                        );

                String line;

                while (
                        (line = reader.readLine())
                                != null
                ) {

                    System.out.println(line);
                }

                int exitCode =
                        process.waitFor();

                System.out.println(
                        "SUBTITLE "
                                + index
                                + " ("
                                + label
                                + ") EXIT: "
                                + exitCode
                );

                Path subtitlePath =
                        Paths.get(outputPath);

                if (Files.exists(subtitlePath)) {

                    fixVttFile(
                            subtitlePath
                    );

                } else {

                    System.out.println(
                            "SUBTITLE FILE NOT CREATED"
                    );
                }

                index++;
            }

            System.out.println(
                    "SUBTITLE EXTRACTION COMPLETE"
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private static void fixVttFile(
            Path path
    ) throws IOException {

        List<String> lines =
                Files.readAllLines(path);

        List<String> fixed =
                new ArrayList<>();

        for (String line : lines) {

            if (line.contains("-->")) {

                String[] parts =
                        line.split("-->");

                String start =
                        normalizeTimestamp(
                                parts[0].trim()
                        );

                String end =
                        normalizeTimestamp(
                                parts[1].trim()
                        );

                fixed.add(
                        start
                                + " --> "
                                + end
                );

            } else {

                fixed.add(line);

            }
        }

        Files.write(path, fixed);
    }

    private static String normalizeTimestamp(
            String ts
    ) {

        if (
                ts.matches(
                        "^\\d{2}:\\d{2}\\.\\d{3}$"
                )
        ) {

            return "00:" + ts;
        }

        return ts;
    }

    private static String normalizeLanguage(
            String lang
    ) {

        if (lang == null) {
            return "und";
        }

        lang = lang.toLowerCase();

        switch (lang) {

            case "spa":
            case "es":
                return "es";

            case "eng":
            case "en":
                return "en";

            case "jpn":
            case "jp":
            case "ja":
                return "ja";

            case "por":
            case "pt":
                return "pt";

            default:
                return lang;
        }
    }

    private static String normalizeLabel(
            String lang
    ) {

        switch (lang) {

            case "es":
                return "Español";

            case "en":
                return "English";

            case "ja":
                return "Japanese";

            case "pt":
                return "Português";

            default:
                return "Unknown";
        }
    }
}