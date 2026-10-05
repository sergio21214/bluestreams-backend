package com.spmf;

import com.spmf.dto.MusicHomeDto;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/music/home")
@Produces(MediaType.APPLICATION_JSON)
public class MusicHomeResource {

    @GET
    public MusicHomeDto home(

            @HeaderParam("X-Profile-Id")
            Long profileId

    ){

        MusicHomeDto dto =
                new MusicHomeDto();

        dto.continuePlaying =
                MusicHistory.find(
                                "profileId=?1 order by lastPlayed desc",
                                profileId
                        )
                        .firstResultOptional()
                        .map(h -> new MusicHistoryResource().toDto((MusicHistory) h))
                        .orElse(null);

        dto.onRepeat =
                new MusicHistoryResource()
                        .repeat(profileId);

        dto.mostPlayed =
                new MusicHistoryResource()
                        .mostPlayed(profileId);

        dto.favorites =
                java.util.List.of();

        return dto;
    }

}