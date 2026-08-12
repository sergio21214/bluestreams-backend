package com.spmf.iptv;

import com.spmf.IPTVImportService;
import com.spmf.iptv.client.IPTVClient;
import com.spmf.iptv.client.IPTVClientFactory;
import com.spmf.iptv.config.IPTVConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/iptv/test")
public class IPTVTestResource {

    @Inject
    IPTVClientFactory factory;

    @Inject
    IPTVImportService importer;

    @Inject
    IPTVConfig config;

    @GET
    public String test() throws Exception {

        IPTVConfig.Provider provider =
                config.providers().get(0);

        IPTVClient client =
                factory.get(provider);

        importer.importFromStream(
                client.downloadPlaylist(provider),
                provider.name()
        );

        return "Imported";
    }
}