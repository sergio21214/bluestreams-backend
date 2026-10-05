package com.spmf;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/movies")
public class GreetingResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<MediaItem> movies() {

        return MediaItem.listAll();

    }
}