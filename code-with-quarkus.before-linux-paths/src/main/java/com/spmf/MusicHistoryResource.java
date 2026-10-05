package com.spmf;


import com.spmf.dto.MusicHistoryDto;
import com.spmf.dto.ProgressRequest;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;

@Path("/music/history")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MusicHistoryResource {

    @POST
    @Transactional
    public Response save(
            ProgressRequest req,
            @HeaderParam("X-Profile-Id")
            Long profileId
    ) {
        System.out.println("========== MUSIC HISTORY ==========");
        System.out.println("Profile: " + profileId);

        if (req != null) {
            System.out.println("Media: " + req.mediaId);
            System.out.println("Position: " + req.positionSeconds);
            System.out.println("Completed: " + req.completed);
        }


        MusicHistory history =
                MusicHistory.find(
                        "profileId=?1 and mediaId=?2",
                        profileId,
                        req.mediaId
                ).firstResult();

        if (history == null) {

            history = new MusicHistory();

            history.profileId =
                    profileId;

            history.mediaId =
                    req.mediaId;

            history.playCount = 0;
            history.counted = false;
        }

        System.out.println("------");
        System.out.println("position = " + req.positionSeconds);
        System.out.println("counted before = " + history.counted);
        System.out.println("playCount before = " + history.playCount);

        // User started the song from the beginning
        if (req.positionSeconds < 5) {
            history.counted = false;
        }

        history.lastPositionSeconds =
                req.positionSeconds;

        history.lastPlayed =
                LocalDateTime.now();

        if (
                !Boolean.TRUE.equals(history.counted)
                        &&
                        req.positionSeconds >= 30
        ) {

            history.playCount++;

            history.counted = true;
        }
        System.out.println("counted after = " + history.counted);
        System.out.println("playCount after = " + history.playCount);

        history.persist();

        return Response.ok().build();
    }

    @GET
    @Path("/resume/{mediaId}")
    public MusicHistory resume(

            @PathParam("mediaId")
            Long mediaId,

            @HeaderParam("X-Profile-Id")
            Long profileId

    ) {

        return MusicHistory.find(

                "profileId=?1 and mediaId=?2",

                profileId,

                mediaId

        ).firstResult();

    }

    @GET
    @Path("/recent")
    public List<MusicHistoryDto> recent(

            @HeaderParam("X-Profile-Id")
            Long profileId

    ) {

        List<MusicHistory> history =

                MusicHistory.list(

                        "profileId=?1 order by lastPlayed desc",

                        profileId

                );

        return history.stream()

                .map(this::toDto)

                .toList();

    }

    @GET
    @Path("/repeat")
    public List<MusicHistoryDto> repeat(

            @HeaderParam("X-Profile-Id")
            Long profileId

    ) {

        List<MusicHistory> history =

                MusicHistory.list(

                        "profileId=?1 order by playCount desc, lastPlayed desc",

                        profileId

                );

        return history.stream()

                .map(this::toDto)

                .toList();

    }

    MusicHistoryDto toDto(
            MusicHistory history
    ) {

        MediaItem media =
                MediaItem.findById(
                        history.mediaId
                );

        if (media == null) {

            return null;

        }

        MusicHistoryDto dto =
                new MusicHistoryDto();


        dto.id =
                media.id;
        dto.mediaId = media.id;

        dto.title =
                media.title;

        dto.artist =
                media.artist;

        dto.album =
                media.album;

        dto.poster =
                media.poster;

        dto.playCount =
                history.playCount;

        dto.positionSeconds =
                history.lastPositionSeconds;

        dto.lastPlayed =
                history.lastPlayed;

        return dto;
    }

    @GET
    @Path("/most-played")
    public List<MusicHistoryDto> mostPlayed(

            @HeaderParam("X-Profile-Id")
            Long profileId

    ) {

        List<MusicHistory> history =

                MusicHistory.list(

                        "profileId=?1 order by playCount desc",

                        profileId

                );

        return history.stream()

                .map(this::toDto)

                .filter(java.util.Objects::nonNull)

                .toList();
    }

    @GET
    @Path("/top")
    public List<MusicHistoryDto> top(
            @HeaderParam("X-Profile-Id")
            Long profileId
    ) {


        List<MusicHistory> history =

                MusicHistory.list(

                        "profileId=?1 order by playCount desc limit 20",

                        profileId

                );

        return history.stream()

                .map(this::toDto)

                .filter(java.util.Objects::nonNull)

                .toList();
    }



}