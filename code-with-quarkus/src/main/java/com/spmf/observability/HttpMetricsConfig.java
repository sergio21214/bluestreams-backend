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

        if (id.getName().startsWith("http.server.requests")) {

            return DistributionStatisticConfig.builder()
                    .percentilesHistogram(true)
                    .build()
                    .merge(config);
        }

        return config;
    }
}
