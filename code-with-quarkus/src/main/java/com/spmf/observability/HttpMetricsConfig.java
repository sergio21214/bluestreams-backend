package com.spmf.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import jakarta.inject.Singleton;

@Singleton
public class HttpMetricsConfig implements MeterFilter {

    @Override
    public DistributionStatisticConfig configure(
            Meter.Id id,
            DistributionStatisticConfig config
    ) {

        String name = id.getName();

        if (name.startsWith("http.server.requests")
                || name.startsWith("music.download")
                || name.startsWith("iptv.check")) {

            return DistributionStatisticConfig.builder()
                    .percentilesHistogram(true)
                    .build()
                    .merge(config);
        }

        return config;
    }
}
