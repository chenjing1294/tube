package com.serene.tube;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Output implements Plugin {
    private final static Logger logger = LoggerFactory.getLogger(Output.class);
    protected OutputConfig config;
    private Meter meter;
    private Timer timer;

    public Output(OutputConfig config) {
        this.config = config;
        if (config.getEnableMeter() != null && config.getEnableMeter()) {
            this.meter = metricRegistry.meter(MetricRegistry.name(this.getClass(), "meter"));
            this.timer = metricRegistry.timer(MetricRegistry.name(this.getClass(), "timer"));
        }
    }


    protected Event beforeEmit(Event event) {
        return event;
    }

    void process(Event event) {
        Timer.Context context = null;
        try {
            if (config.getEnableMeter() != null && config.getEnableMeter()) {
                this.meter.mark();
                context = timer.time();
            }
            event = beforeEmit(event);
            emit(event);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (context != null)
                context.stop();
        }
    }

    protected abstract void emit(Event event);
}
