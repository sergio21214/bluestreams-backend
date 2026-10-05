package com.spmf;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.quarkus.runtime.StartupEvent;
import jakarta.inject.Inject;

@ApplicationScoped
public class IPTVStartup {

    @Inject
    IPTVScheduler scheduler;

    void onStart(@Observes StartupEvent ev) throws Exception {

        //scheduler.refresh();

    }

}