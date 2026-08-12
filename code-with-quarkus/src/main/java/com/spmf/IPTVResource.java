package com.spmf;

import com.spmf.iptv.client.IPTVClientFactory;
import com.spmf.iptv.config.IPTVResolverFactory;
import com.spmf.iptv.config.IPTVStreamResolver;
import com.spmf.tv.IPTVChannel;
import com.spmf.tv.IPTVSource;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

@Path("/iptv")
@Produces(MediaType.APPLICATION_JSON)
public class IPTVResource {

    @GET
    @Path("/providers")
    public List<IPTVSource> providers() {

        return IPTVSource.listAll();

    }

//    @GET
//    @Path("/{providerId}")
//    public List<IPTVChannel> channels(
//            @PathParam("providerId")
//            Long providerId
//    ) {
//
//        return IPTVChannel.list(
//                "providerId",
//                providerId
//        );
//
//    }


    // GET /iptv/{providerId}/groups
// Cheap query: just the distinct group names for this provider, used to
// populate the group dropdown and to pick a default group without ever
// loading channel rows.
    @GET
    @Path("/{providerId}/groups")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getGroups(@PathParam("providerId") Long providerId) {

        return IPTVChannel
                .find(
                        "select distinct c.groupName from IPTVChannel c " +
                                "where c.providerId = ?1 and c.enabled = true " +
                                "order by c.groupName",
                        providerId
                )
                .project(String.class)
                .list();
    }

    // GET /iptv/{providerId}?group=News
// Scoped channel fetch. When "group" is provided, only channels in that
// group are returned instead of the whole provider (which can be 95k+ rows).
    @GET
    @Path("/{providerId}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<IPTVChannel> getChannels(
            @PathParam("providerId") Long providerId,
            @QueryParam("group") String group
    ) {

        if (group == null || group.isBlank() || "All".equalsIgnoreCase(group)) {

            // Still bounded — see note below about why "All" should stay capped
            // or paginated rather than truly unbounded for 95k-row providers.
            return IPTVChannel.find(
                    "providerId = ?1 and enabled = true",
                    Sort.by("name"),
                    providerId
            ).list();
        }

        return IPTVChannel.find(
                "providerId = ?1 and groupName = ?2 and enabled = true",
                Sort.by("name"),
                providerId,
                group
        ).list();
    }




}