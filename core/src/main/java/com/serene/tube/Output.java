package com.serene.tube;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

public abstract class Output implements Plugin {
    protected OutputConfig config;
    private Meter meter;

    public Output(OutputConfig config) {
        registerShutdownHook();
        this.config = config;
        if (config.getEnableMeter() != null && config.getEnableMeter()) {
            this.meter = metricRegistry.meter(MetricRegistry.name(this.getClass()));
        }
    }


    protected Event beforeEmit(Event event) {
        return event;
    }

    void process(Event event) {
        event = beforeEmit(event);
        emit(event);
        if (config.getEnableMeter() != null && config.getEnableMeter()) {
            this.meter.mark();
        }
    }

    protected abstract void emit(Event event);
}
