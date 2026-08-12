package com.spmf.iptv;

import com.spmf.IPTVProvider;
import com.spmf.iptv.config.IPTVStreamResolver;
import com.spmf.iptv.config.IPTVStreamResolverFactory;
import com.spmf.tv.IPTVChannel;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Path("/iptv/hls")
public class IPTVHLSResource {

    @Inject
    IPTVStreamResolverFactory resolverFactory;

    @Inject
    IPTVHLSSessionManager manager;

    private static final Map<Long, Process> running =
            new ConcurrentHashMap<>();

    @GET
    @Path("/start/{channelId}")
    public Response start(
            @PathParam("channelId") Long channelId
    ) throws Exception {

        IPTVChannel channel =
                IPTVChannel.findById(channelId);

        if (channel == null)
            return Response.status(404).build();

        IPTVProvider provider =
                IPTVProvider.findById(channel.providerId);

        IPTVStreamResolver resolver =
                resolverFactory.get(provider);

        String stream =
                resolver.resolve(provider, channel);

        IPTVHLSSession session =
                manager.get(channelId);

        if (session != null) {

            if (session.process != null &&
                    session.process.isAlive()) {

                session.lastAccess = java.time.Instant.now();

                File playlist =
                        new File(session.folder, "playlist.m3u8");

                if (playlist.exists()) {

                    Log.info("Reusing HLS session for channel " + channelId);

                    return Response.ok(playlist)
                            .type("application/vnd.apple.mpegurl")
                            .header("Cache-Control", "no-cache")
                            .build();

                }

            } else {

                Log.info("Dead HLS session detected for channel " + channelId);

                manager.remove(channelId);

            }

        }

        File folder =
                new File("iptv-hls/" + channel.id);

        folder.mkdirs();

        Process existing =
                running.get(channelId);

        if (existing != null && existing.isAlive()) {

            return Response.ok().build();

        }

        ProcessBuilder pb =
                new ProcessBuilder(

                        "ffmpeg",

                        "-reconnect",
                        "1",

                        "-reconnect_streamed",
                        "1",

                        "-reconnect_delay_max",
                        "10",

                        "-rw_timeout",
                        "15000000",

                        "-i",
                        stream,

                        "-c",
                        "copy",

                        "-f",
                        "hls",

                        "-hls_time",
                        "4",

                        "-hls_list_size",
                        "6",

                        "-hls_flags",
                        "delete_segments+append_list+omit_endlist",

                        new File(folder,
                                "playlist.m3u8")
                                .getAbsolutePath()

                );

        pb.redirectErrorStream(true);

        Process process =
                pb.start();

        session =
                new IPTVHLSSession();

        session.channelId = channelId;

        session.process = process;

        session.folder = folder;

        session.lastAccess = java.time.Instant.now();

        manager.put(session);

        Log.info("Created HLS session for channel " + channelId);

        running.put(channelId, process);

        File playlist =
                new File(folder, "playlist.m3u8");

        long timeout =
                System.currentTimeMillis() + 15000;

        while (true) {

            if (playlist.exists() &&
                    playlist.length() > 0) {

                break;

            }

            if (!process.isAlive()) {

                running.remove(channelId);
                manager.remove(channelId);

                return Response.serverError()
                        .entity("FFmpeg exited")
                        .build();

            }

            if (System.currentTimeMillis() > timeout) {

                process.destroyForcibly();

                running.remove(channelId);
                manager.remove(channelId);

                return Response.serverError()
                        .entity("Timeout waiting playlist")
                        .build();

            }

            Thread.sleep(200);

        }

        new Thread(() -> {

            try {

                process.getInputStream()
                        .transferTo(System.out);

                int exit =
                        process.waitFor();

                Log.info(
                        "FFmpeg stopped channel "
                                + channelId
                                + " exit="
                                + exit
                );


            } catch (Exception e) {

                Log.error(
                        "FFmpeg monitor error",
                        e
                );

            }

            running.remove(channelId);

        }).start();


        return Response.ok().build();

    }

}