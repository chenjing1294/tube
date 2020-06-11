package com.serene.tube;

import com.codahale.metrics.MetricRegistry;

public interface Metric {
    MetricRegistry metricRegistry = new MetricRegistry();

    default void shutdown() {
    }

    default void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }
}
