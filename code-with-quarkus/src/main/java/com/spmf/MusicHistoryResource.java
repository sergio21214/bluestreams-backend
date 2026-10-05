package com.spmf;

import com.spmf.MediaItem;
import com.spmf.MusicHistory;
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

    private static final long COUNT_THRESHOLD_SECONDS = 30;

    @POST
    @Transactional
    public Response save(
            ProgressRequest req,
            @HeaderParam("X-Profile-Id") Long profileId
    ) {

        System.out.println("========== MUSIC HISTORY ==========");

        if (profileId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing X-Profile-Id")
                    .build();
        }

        if (req == null || req.mediaId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing mediaId")
                    .build();
        }

        System.out.println("Profile: " + profileId);
        System.out.println("Media: " + req.mediaId);
        System.out.println("Position: " + req.positionSeconds);
        System.out.println("Completed: " + req.completed);
        System.out.println("New Session: " + req.newSession);

        MusicHistory history =
                MusicHistory.find(
                        "profileId=?1 and mediaId=?2",
                        profileId,
                        req.mediaId
                ).firstResult();

        /*
         * Create history row if this song has never been played.
         */
        if (history == null) {

            history = new MusicHistory();

            history.profileId = profileId;
            history.mediaId = req.mediaId;

            history.playCount = 0;
            history.counted = false;

            history.lastPositionSeconds = 0;
            history.sessionStartPositionSeconds = 0;
        }

        System.out.println("------ BEFORE ------");
        System.out.println(
                "playCount = " + history.playCount
        );
        System.out.println(
                "counted = " + history.counted
        );
        System.out.println(
                "lastPosition = " +
                        history.lastPositionSeconds
        );
        System.out.println(
                "sessionStart = " +
                        history.sessionStartPositionSeconds
        );

        /*
         * A NEW SESSION explicitly resets the counted state.
         *
         * IMPORTANT:
         *
         * We remember the position where the new session started.
         *
         * Example:
         *
         * Previous session stopped at 82 seconds.
         *
         * User starts the song again at 82 seconds.
         *
         * sessionStartPosition = 82
         *
         * They must then listen for another 30 seconds:
         *
         * 82 -> 112
         *
         * before getting another play count.
         */
        if (req.newSession) {

            history.counted = false;

            history.sessionStartPositionSeconds =
                    req.positionSeconds;

            System.out.println(
                    "NEW PLAYBACK SESSION STARTED"
            );
        }

        /*
         * Always update the latest playback position.
         */
        history.lastPositionSeconds =
                req.positionSeconds;

        history.lastPlayed =
                LocalDateTime.now();

        /*
         * Calculate how many seconds have actually been
         * listened to during THIS playback session.
         *
         * This is important for resume.
         */
        long sessionElapsed =
                req.positionSeconds -
                        history.sessionStartPositionSeconds;

        /*
         * Protect against seeking backwards.
         *
         * If the user seeks backwards, elapsed time must not
         * become negative.
         */
        if (sessionElapsed < 0) {
            sessionElapsed = 0;
        }

        System.out.println(
                "sessionElapsed = " + sessionElapsed
        );

        /*
         * Count exactly once per playback session.
         *
         * A completed track also counts, even if it was
         * shorter than 30 seconds.
         */
        if (
                !history.counted &&
                        (
                                sessionElapsed >= COUNT_THRESHOLD_SECONDS ||
                                        req.completed
                        )
        ) {

            history.playCount++;

            history.counted = true;

            System.out.println(
                    "PLAY COUNT INCREMENTED"
            );
        }

        System.out.println("------ AFTER ------");
        System.out.println(
                "playCount = " + history.playCount
        );
        System.out.println(
                "counted = " + history.counted
        );
        System.out.println(
                "sessionStart = " +
                        history.sessionStartPositionSeconds
        );

        history.persist();

        return Response.ok().build();
    }


    @GET
    @Path("/resume/{mediaId}")
    public MusicHistory resume(

            @PathParam("mediaId") Long mediaId,

            @HeaderParam("X-Profile-Id") Long profileId

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

            @HeaderParam("X-Profile-Id") Long profileId

    ) {

        List<MusicHistory> history =

                MusicHistory.list(

                        "profileId=?1 order by lastPlayed desc",

                        profileId

                );

        return history.stream()

                .map(this::toDto)

                .filter(java.util.Objects::nonNull)

                .toList();

    }


    @GET
    @Path("/repeat")
    public List<MusicHistoryDto> repeat(

            @HeaderParam("X-Profile-Id") Long profileId

    ) {

        List<MusicHistory> history =

                MusicHistory.list(

                        "profileId=?1 order by playCount desc, lastPlayed desc",

                        profileId

                );

        return history.stream()

                .map(this::toDto)

                .filter(java.util.Objects::nonNull)

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

        dto.mediaId =
                media.id;

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

            @HeaderParam("X-Profile-Id") Long profileId

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

            @HeaderParam("X-Profile-Id") Long profileId

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

