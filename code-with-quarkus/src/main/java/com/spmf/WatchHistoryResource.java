package com.spmf;

import com.spmf.dto.ContinueWatchingDto;
import com.spmf.dto.ProgressRequest;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Path("/history")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WatchHistoryResource {

    @POST
    @Transactional
    public Response save(
            ProgressRequest req,
            @HeaderParam("X-Profile-Id")
            Long profileId
    ) {

        System.out.println("========== HISTORY ==========");
        System.out.println("PROFILE ID: " + profileId);
        System.out.println("REQUEST: " + req);

        if (req != null) {
            System.out.println("MEDIA ID: " + req.mediaId);
            System.out.println("POSITION: " + req.positionSeconds);
            System.out.println("COMPLETED: " + req.completed);
        }

        WatchHistory history =
                WatchHistory.find(
                        "profileId=?1 and mediaId=?2",
                        profileId,
                        req.mediaId
                ).firstResult();

        if (history == null) {

            history =
                    new WatchHistory();

            history.profileId =
                    profileId;

            history.mediaId =
                    req.mediaId;
        }

        history.positionSeconds =
                req.positionSeconds;

        history.completed =
                req.completed;

        history.lastWatched =
                LocalDateTime.now();

        history.persist();

        return Response.ok().build();
    }

    @GET
    @Path("/resume/{mediaId}")
    public WatchHistory resume(
            @PathParam("mediaId")
            Long mediaId,

            @HeaderParam("X-Profile-Id")
            Long profileId
    ) {

        return WatchHistory.find(
                "profileId=?1 and mediaId=?2",
                profileId,
                mediaId
        ).firstResult();
    }

    @GET
    @Path("/continue")
    public List<ContinueWatchingDto> continueWatching(
            @HeaderParam("X-Profile-Id")
            Long profileId
    ) {

        List<WatchHistory> historyList =
                WatchHistory.list(
                        "profileId=?1 and completed=false order by lastWatched desc",
                        profileId
                );

        List<ContinueWatchingDto> result =
                new ArrayList<>();

        for (WatchHistory history : historyList) {

            MediaItem media =
                    MediaItem.findById(
                            history.mediaId
                    );

            if (media == null) {
                continue;
            }

            ContinueWatchingDto dto =
                    new ContinueWatchingDto();

            dto.mediaId =
                    media.id;

            dto.title =
                    media.title;

            dto.poster =
                    media.poster;

            dto.mediaType =
                    media.mediaType;

            dto.seasonNumber =
                    media.seasonNumber;

            dto.episodeNumber =
                    media.episodeNumber;

            dto.positionSeconds =
                    history.positionSeconds;

            dto.completed =
                    history.completed;

            result.add(dto);
        }

        return result;
    }
}