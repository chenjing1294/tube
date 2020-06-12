package com.serene.tube;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

public abstract class Filter extends Thread implements Metric {
    private static final Logger logger = LoggerFactory.getLogger(Filter.class);
    protected final FilterConfig config;
    private BlockingQueue<Event> preQueue;
    private BlockingQueue<Event> postQueue;
    private Meter meter;

    public Filter(FilterConfig config, String threadName) {
        super(threadName);
        registerShutdownHook();
        this.config = config;
        if (config.getEnableMeter() != null && config.getEnableMeter()) {
            this.meter = metricRegistry.meter(MetricRegistry.name(this.getClass()));
        }
    }

    BlockingQueue<Event> getPreQueue() {
        return preQueue;
    }

    void setPreQueue(BlockingQueue<Event> preQueue) {
        this.preQueue = preQueue;
    }

    BlockingQueue<Event> getPostQueue() {
        return postQueue;
    }

    void setPostQueue(BlockingQueue<Event> postQueue) {
        this.postQueue = postQueue;
    }

    protected Event postProcess(Event event) {
        return event;
    }

    protected Event preProcess(Event event) {
        return event;
    }

    public abstract Event filter(Event event);

    private Event process(Event event) {
        event = preProcess(event);
        event = filter(event);
        event = postProcess(event);
        if (config.getEnableMeter() != null && config.getEnableMeter()) {
            meter.mark();
        }
        return event;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Event event = preQueue.take();
                event = process(event);
                postQueue.put(event);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}