package com.spmf.iptv;

import com.spmf.IPTVProvider;
import com.spmf.iptv.config.IPTVStreamResolver;
import com.spmf.iptv.config.IPTVStreamResolverFactory;
import com.spmf.tv.IPTVChannel;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Path("/iptv/proxy")
public class IPTVProxyResource {

    @Inject
    IPTVStreamResolverFactory resolverFactory;

    private final HttpClient client =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

    @GET
    @Path("/{channelId}")
    public Response proxy(
            @PathParam("channelId") Long channelId
    ) throws Exception {

        IPTVChannel channel =
                IPTVChannel.findById(channelId);

        if (channel == null) {
            return Response.status(404).build();
        }

        Log.info("Channel id: " + channel.id);
        Log.info("ProviderId: " + channel.providerId);
        Log.info("ProviderName: " + channel.providerName);
        Log.info("Stream: " + channel.streamUrl);

        IPTVProvider provider =
                IPTVProvider.findById(channel.providerId);

        if (provider == null) {
            return Response.status(404).build();
        }

        IPTVStreamResolver resolver =
                resolverFactory.get(provider);

        String streamUrl =
                resolver.resolve(provider, channel);

        Log.info("Resolved URL: " + streamUrl);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(streamUrl))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Accept", "*/*")
                        .GET()
                        .build();

        HttpResponse<InputStream> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );

        Log.info("Final URI: " + response.uri());
        Log.info("Status: " + response.statusCode());

        response.headers().map().forEach(
                (k, v) -> Log.info(k + " = " + v)
        );

        String type =
                response.headers()
                        .firstValue("Content-Type")
                        .orElse("video/mp2t");


        return Response.ok(response.body())
                .type(type)
                .build();
    }
}