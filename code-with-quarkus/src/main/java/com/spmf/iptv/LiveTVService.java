package com.spmf.iptv;

import com.spmf.IPTVProvider;
import com.spmf.iptv.config.IPTVStreamResolver;
import com.spmf.iptv.config.IPTVStreamResolverFactory;
import com.spmf.tv.IPTVChannel;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class LiveTVService {

    @Inject
    IPTVStreamResolverFactory resolverFactory;

    private final Map<Long, Process> processes =
            new ConcurrentHashMap<>();

    public File start(IPTVChannel channel, IPTVProvider provider) throws Exception {

        File folder = new File(
                "live/channel-" + channel.id
        );

        folder.mkdirs();

        File playlist =
                new File(folder, "playlist.m3u8");

        if (playlist.exists()) {
            return playlist;
        }

        IPTVStreamResolver resolver =
                resolverFactory.get(provider);

        String streamUrl =
                resolver.resolve(provider, channel);

        Log.info("Starting FFmpeg for " + channel.name);

        ProcessBuilder pb =
                new ProcessBuilder(

                        "ffmpeg",

                        "-y",

                        "-i",
                        streamUrl,

                        "-c",
                        "copy",

                        "-f",
                        "hls",

                        "-hls_time",
                        "2",

                        "-hls_list_size",
                        "6",

                        "-hls_flags",
                        "delete_segments+append_list+omit_endlist",

                        playlist.getAbsolutePath()

                );

        pb.redirectErrorStream(true);

        Process process =
                pb.start();

        processes.put(
                channel.id,
                process
        );

        return playlist;

    }

}