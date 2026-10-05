package com.spmf;

import java.io.File;
import java.io.IOException;

public class ThumbnailService {

    public static void generateThumbnail(
            String inputPath
    ) {

        try {

            File inputFile =
                    new File(inputPath);

            String cleanName =
                    FilenameUtils.cleanName(
                            inputFile.getName()
                                    .replace(".mp4", "")
                                    .replace(".mkv", "")
                                    .replace(".avi", "")
                    );

            String outputPath =
                    "/mnt/bluestreams/SPMF/thumbnails/"
                            + cleanName
                            + ".jpg";

            File thumbnail =
                    new File(outputPath);

            if (thumbnail.exists()) {

                System.out.println(
                        "THUMBNAIL EXISTS: "
                                + cleanName
                );

                return;
            }

            System.out.println(
                    "GENERATING THUMBNAIL: "
                            + inputPath
            );

            ProcessBuilder builder =
                    new ProcessBuilder(

                            "ffmpeg",

                            "-y",

                            "-ss",
                            "00:00:05",

                            "-i",
                            inputPath,

                            "-frames:v",
                            "1",

                            "-update",
                            "1",

                            outputPath
                    );

            builder.inheritIO();

            Process process =
                    builder.start();

            int exitCode =
                    process.waitFor();

            System.out.println(
                    "THUMBNAIL EXIT CODE: "
                            + exitCode
            );

        } catch (
                IOException
                | InterruptedException e
        ) {

            e.printStackTrace();
        }
    }
}