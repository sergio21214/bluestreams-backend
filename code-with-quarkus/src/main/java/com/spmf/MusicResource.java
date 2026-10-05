package com.spmf;

import com.spmf.dto.AlbumDto;
import com.spmf.dto.ArtistDto;
import com.spmf.dto.MusicLibraryDto;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.RolesAllowed;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.nio.file.Files;
import com.spmf.music.MusicDownloadJob;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;

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
        if (lower.endsWith(".webm")) {
            return "audio/webm";
        }

        if (lower.endsWith(".opus")) {
            return "audio/opus";
        }

        return "application/octet-stream";
    }

    @GET
    @Path("/library")
    @RolesAllowed({"USER", "ADMIN"})
    public MusicLibraryDto library(
            @QueryParam("genre") String genre,
            @QueryParam("artist") String artist,
            @QueryParam("album") String album
    ) {

        List<MediaItem> tracks =
                MediaItem.list(
                        "mediaType",
                        "music"
                );

        /*
         * Apply optional filters.
         *
         * The filtering happens before the artist/album
         * hierarchy is built, so the frontend receives
         * exactly the same MusicLibraryDto structure.
         */

        if (genre != null && !genre.isBlank()) {

            String normalizedGenre =
                    genre.trim();

            tracks =
                    tracks.stream()
                            .filter(t ->
                                    t.genre != null &&
                                            t.genre.equalsIgnoreCase(
                                                    normalizedGenre
                                            )
                            )
                            .toList();
        }

        if (artist != null && !artist.isBlank()) {

            String normalizedArtist =
                    artist.trim();

            tracks =
                    tracks.stream()
                            .filter(t ->
                                    t.artist != null &&
                                            t.artist.equalsIgnoreCase(
                                                    normalizedArtist
                                            )
                            )
                            .toList();
        }

        if (album != null && !album.isBlank()) {

            String normalizedAlbum =
                    album.trim();

            tracks =
                    tracks.stream()
                            .filter(t ->
                                    t.album != null &&
                                            t.album.equalsIgnoreCase(
                                                    normalizedAlbum
                                            )
                            )
                            .toList();
        }

        /*
         * Build:
         *
         * Artist
         *   └── Album
         *         └── Tracks
         */

        Map<String,
                Map<String, List<MediaItem>>> grouped =
                tracks.stream()
                        .collect(
                                Collectors.groupingBy(
                                        t ->
                                                t.artist != null
                                                        ? t.artist
                                                        : "Unknown Artist",

                                        Collectors.groupingBy(
                                                t ->
                                                        t.album != null
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
                String artistName :
                grouped.keySet()
        ) {

            ArtistDto artistDto =
                    new ArtistDto();

            artistDto.name =
                    artistName;

            artistDto.albums =
                    new ArrayList<>();

            Map<String, List<MediaItem>> albums =
                    grouped.get(
                            artistName
                    );

            for (
                    String albumName :
                    albums.keySet()
            ) {

                AlbumDto albumDto =
                        new AlbumDto();

                albumDto.name =
                        albumName;

                List<MediaItem> albumTracks =
                        albums.get(
                                albumName
                        );

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

                if (!albumTracks.isEmpty()) {

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

    @GET
    @Path("/music-art/{filename}")
    @Produces({
            "image/jpeg",
            "image/png",
            "image/webp"
    })
    public Response getMusicArtwork(
            @PathParam("filename") String filename
    ) {

        java.nio.file.Path artwork =
                java.nio.file.Path.of(
                        "/mnt/bluestreams/SPMF/media/music/music-art",
                        filename
                );

        if (!Files.exists(artwork)) {

            return Response
                    .status(Response.Status.NOT_FOUND)
                    .build();
        }

        String contentType =
                determineImageContentType(
                        filename
                );

        return Response.ok(
                artwork.toFile(),
                contentType
        ).build();
    }

    private String determineImageContentType(
            String filename
    ) {

        String name =
                filename.toLowerCase();

        if (name.endsWith(".png")) {
            return "image/png";
        }

        if (name.endsWith(".webp")) {
            return "image/webp";
        }

        return "image/jpeg";
    }


}