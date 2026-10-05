package com.spmf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HlsService {

    public static void generateHls(
            String inputPath,
            String videoFilename
    ) {

        try {

            String movieName =
                    FilenameUtils.cleanName(
                            videoFilename
                    );

            File outputDir =
                    new File(
                            "/mnt/bluestreams/SPMF/hls/" + movieName
                    );

            boolean dashAlreadyExists =
                    outputDir.exists();

            if (!dashAlreadyExists) {

                outputDir.mkdirs();
            }

            String outputPath =
                    "/mnt/bluestreams/SPMF/hls/" + movieName;

            List<AudioTrack> audioTracks =
                    FFprobeService.getAudioTracks(
                            inputPath
                    );

            System.out.println(
                    "AUDIO TRACKS FOUND: "
                            + audioTracks.size()
            );

            List<String> command =
                    new ArrayList<>();

            command.add("ffmpeg");

            command.add("-y");

            command.add("-i");

            command.add(inputPath);

            // VIDEO

            // VIDEO STREAMS

// 1080p

            command.add("-map");
            command.add("0:v:0");

            command.add("-c:v:0");
            command.add("h264_nvenc");

            command.add("-preset:v:0");
            command.add("p1");

            command.add("-b:v:0");
            command.add("8M");

            command.add("-maxrate:v:0");
            command.add("10M");

            command.add("-bufsize:v:0");
            command.add("16M");

            command.add("-s:v:0");
            command.add("1920x1080");

            command.add("-pix_fmt:v:0");
            command.add("yuv420p");

// 720p

            command.add("-map");
            command.add("0:v:0");

            command.add("-c:v:1");
            command.add("h264_nvenc");

            command.add("-preset:v:1");
            command.add("p1");

            command.add("-b:v:1");
            command.add("4M");

            command.add("-maxrate:v:1");
            command.add("5M");

            command.add("-bufsize:v:1");
            command.add("8M");

            command.add("-s:v:1");
            command.add("1280x720");

            command.add("-pix_fmt:v:1");
            command.add("yuv420p");

// 480p

            command.add("-map");
            command.add("0:v:0");

            command.add("-c:v:2");
            command.add("h264_nvenc");

            command.add("-preset:v:2");
            command.add("p1");

            command.add("-b:v:2");
            command.add("1500k");

            command.add("-maxrate:v:2");
            command.add("2M");

            command.add("-bufsize:v:2");
            command.add("4M");

            command.add("-s:v:2");
            command.add("854x480");

            command.add("-pix_fmt:v:2");
            command.add("yuv420p");

            // AUDIO TRACKS

            int audioCounter = 0;

            for (AudioTrack track : audioTracks) {

                command.add("-map");

                command.add(
                        "0:a:" + audioCounter + "?"
                );

                command.add(
                        "-c:a:" + audioCounter
                );

                command.add("aac");

                command.add(
                        "-b:a:" + audioCounter
                );

                command.add("192k");

                command.add(
                        "-metadata:s:a:"
                                + audioCounter
                );

                command.add(
                        "language="
                                + track.language
                );

                command.add(
                        "-metadata:s:a:"
                                + audioCounter
                );

                command.add(
                        "title="
                                + track.language
                );

                audioCounter++;
            }

            // ADAPTATION SETS

            String adaptation =
                    "id=0,streams=0,1,2";

            for (
                    int i = 0;
                    i < audioTracks.size();
                    i++
            ) {

                adaptation +=
                        " id="
                                + (i + 1)
                                + ",streams="
                                + (i + 3);
            }

            command.add("-adaptation_sets");

            command.add(adaptation);

            // DASH

            command.add("-f");

            command.add("dash");

            command.add("-streaming");

            command.add("1");

            command.add("-dash_segment_type");

            command.add("mp4");

            command.add("-seg_duration");

            command.add("4");

            command.add("-use_template");

            command.add("1");

            command.add("-use_timeline");

            command.add("1");

            command.add("-init_seg_name");

            command.add(
                    "init-$RepresentationID$.m4s"
            );

            command.add("-media_seg_name");

            command.add(
                    "chunk-$RepresentationID$-$Number%05d$.m4s"
            );

            // OUTPUT

            command.add(
                    outputPath
                            + "/manifest.mpd"
            );

            System.out.println(command);

            if (dashAlreadyExists) {

                System.out.println(
                        "DASH ALREADY EXISTS"
                );

                return;
            }

            ProcessBuilder builder =
                    new ProcessBuilder(command);

            builder.inheritIO();

            builder.redirectErrorStream(
                    true
            );

            Process process =
                    builder.start();

            process.getOutputStream().close();

            int exitCode =
                    process.waitFor();

            System.out.println(
                    "FFMPEG EXIT CODE: "
                            + exitCode
            );

            System.out.println(
                    "DASH GENERATED"
            );

            SubtitleService.extractSubtitles(
                    inputPath,
                    videoFilename
            );

        } catch (
                IOException
                | InterruptedException e
        ) {

            e.printStackTrace();
        }
    }
}