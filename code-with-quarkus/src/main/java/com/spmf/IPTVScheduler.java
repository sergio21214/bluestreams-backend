package com.spmf;


import com.spmf.iptv.client.IPTVClient;
import com.spmf.iptv.client.IPTVClientFactory;
import com.spmf.iptv.config.IPTVConfig;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.InputStream;

@ApplicationScoped
public class IPTVScheduler {

    @Inject
    IPTVConfig config;

    @Inject
    IPTVClientFactory factory;

    @Inject
    IPTVImportService importer;

    @Scheduled(every = "12h")
    void refresh() throws Exception {

        for (IPTVConfig.Provider provider : config.providers()) {

            IPTVClient client =
                    factory.get(provider);

            InputStream playlist =
                    client.downloadPlaylist(provider);

            importer.importFromStream(
                    playlist,
                    provider.name()
            );

        }

    }

}