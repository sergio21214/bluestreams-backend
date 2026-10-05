package com.spmf;

import com.spmf.dto.AlbumDto;
import com.spmf.dto.ArtistDto;
import com.spmf.dto.MusicLibraryDto;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.RolesAllowed;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.quarkus.hibernate.orm.panache.Panache.getEntityManager;

@Path("/music")
public class MusicResource {

    @GET
    @Path("/stream/{id}")
    public Response stream(
            @PathParam("id") Long id,
            @HeaderParam("Range") String range
    ) throws IOException {

        MediaItem item =
                MediaItem.findById(id);

        if (item == null) {
            return Response.status(
                    Response.Status.NOT_FOUND
            ).build();
        }

        File file =
                new File(item.filePath);

        if (!file.exists()) {
            return Response.status(
                    Response.Status.NOT_FOUND
            ).build();
        }

        String contentType =
                determineContentType(
                        file.getName()
                );

        long fileLength =
                file.length();

        if (range == null) {

            return Response.ok(file)
                    .type(contentType)
                    .header(
                            "Accept-Ranges",
                            "bytes"
                    )
                    .header(
                            "Content-Length",
                            fileLength
                    )
                    .build();
        }

        String[] ranges =
                range.replace(
                        "bytes=",
                        ""
                ).split("-");

        long start =
                Long.parseLong(
                        ranges[0]
                );

        long end =
                fileLength - 1;

        if (
                ranges.length > 1 &&
                        !ranges[1].isEmpty()
        ) {

            end =
                    Long.parseLong(
                            ranges[1]
                    );
        }

        long length =
                end - start + 1;

        RandomAccessFile raf =
                new RandomAccessFile(
                        file,
                        "r"
                );

        raf.seek(start);

        byte[] data =
                new byte[(int) length];

        raf.readFully(data);

        raf.close();

        return Response.status(206)
                .entity(data)
                .type(contentType)
                .header(
                        "Accept-Ranges",
                        "bytes"
                )
                .header(
                        "Content-Length",
                        length
                )
                .header(
                        "Content-Range",
                        "bytes "
                                + start
                                + "-"
                                + end
                                + "/"
                                + fileLength
                )
                .build();
    }

    private String determineContentType(
            String filename
    ) {

        String lower =
                filename.toLowerCase();

        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }

        if (lower.endsWith(".flac")) {
            return "audio/flac";
        }

        if (lower.endsWith(".m4a")) {
            return "audio/mp4";
        }

        if (lower.endsWith(".aac")) {
            return "audio/aac";
        }

        if (lower.endsWith(".ogg")) {
            return "audio/ogg";
        }

        return "application/octet-stream";
    }

    @GET
    @Path("/library")
    @RolesAllowed({"USER","ADMIN"})
    public MusicLibraryDto library() {

        List<MediaItem> tracks =
                MediaItem.list(
                        "mediaType",
                        "music"
                );

        Map<String,
                Map<String,
                                        List<MediaItem>>> grouped =
                tracks.stream()
                        .collect(
                                Collectors.groupingBy(
                                        t -> t.artist != null
                                                ? t.artist
                                                : "Unknown Artist",
                                        Collectors.groupingBy(
                                                t -> t.album != null
                                                        ? t.album
                                                        : "Unknown Album"
                                        )
                                )
                        );

        MusicLibraryDto dto =
                new MusicLibraryDto();

        dto.artists =
                new ArrayList<>();

        for (
                String artist :
                grouped.keySet()
        ) {

            ArtistDto artistDto =
                    new ArtistDto();

            artistDto.name =
                    artist;

            artistDto.albums =
                    new ArrayList<>();

            Map<String,
                    List<MediaItem>>
                    albums =
                    grouped.get(
                            artist
                    );

            for (
                    String album :
                    albums.keySet()
            ) {

                AlbumDto albumDto =
                        new AlbumDto();

                albumDto.name =
                        album;

                List<MediaItem> albumTracks =
                        albums.get(album);

                albumTracks.sort(
                        Comparator.comparing(
                                t ->
                                        t.trackNumber != null
                                                ? t.trackNumber
                                                : 999
                        )
                );

                albumDto.tracks =
                        albumTracks;

                if (
                        !albumTracks.isEmpty()
                ) {

                    albumDto.artwork =
                            albumTracks.get(0)
                                    .poster;
                }

                artistDto.albums.add(
                        albumDto
                );
            }

            dto.artists.add(
                    artistDto
            );
        }

        dto.artists.sort(
                Comparator.comparing(
                        a -> a.name
                )
        );

        return dto;
    }
}