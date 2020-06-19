package com.serene.tube;

import com.codahale.metrics.MetricRegistry;

public interface Plugin {
    MetricRegistry metricRegistry = new MetricRegistry();

    default void shutdown() {
    }
}
