package com.spmf;



import com.spmf.SubtitleDto;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Path("/subtitles")
public class SubtitleResource {

    private static final String SUBTITLE_PATH =
            "/mnt/bluestreams/SPMF/subtitles";

    @GET
    @Path("/{folder}/list")
    @Produces(MediaType.APPLICATION_JSON)
    public List<SubtitleDto> listSubtitles(
            @PathParam("folder") String folder
    ) throws IOException {

        var subtitleDir =
                Paths.get(
                        SUBTITLE_PATH,
                        folder
                );

        if (!Files.exists(subtitleDir)) {
            return List.of();
        }

        List<SubtitleDto> subtitles =
                new ArrayList<>();

        try (Stream<java.nio.file.Path> paths =
                     Files.list(subtitleDir)) {

            List<java.nio.file.Path> files =
                    paths
                            .filter(path ->
                                    path.toString()
                                            .endsWith(".vtt")
                            )
                            .sorted(
                                    Comparator.comparing(
                                            java.nio.file.Path::toString
                                    )
                            )
                            .toList();

            int index = 0;

            for (var file : files) {

                subtitles.add(
                        new SubtitleDto(
                                file.getFileName().toString(),
                                detectLabel(file.getFileName().toString()),
                                detectLang(file.getFileName().toString())
                        )
                );

                index++;
            }
        }

        return subtitles;
    }

    private String detectLang(String filename) {

        String lower =
                filename.toLowerCase();

        if (lower.contains("spa") ||
                lower.contains("es")) {
            return "es";
        }

        if (lower.contains("eng") ||
                lower.contains("en")) {
            return "en";
        }

        if (lower.contains("jpn") ||
                lower.contains("jp")) {
            return "ja";
        }

        if (lower.contains("pt")) {
            return "pt";
        }

        return "und";
    }

    private String detectLabel(String filename) {

        String lower =
                filename.toLowerCase();

        if (lower.contains("spa") ||
                lower.contains("es")) {
            return "Español";
        }

        if (lower.contains("eng") ||
                lower.contains("en")) {
            return "English";
        }

        if (lower.contains("jpn") ||
                lower.contains("jp") || lower.contains("ja")) {
            return "Japanese";
        }

        if (lower.contains("pt")) {
            return "Português";
        }

        return filename;
    }
}
