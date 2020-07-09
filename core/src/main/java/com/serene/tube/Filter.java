package com.serene.tube;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;
import java.util.concurrent.BlockingQueue;

public abstract class Filter extends Thread implements Plugin {
    private static final Logger logger = LoggerFactory.getLogger(Filter.class);
    protected final FilterConfig config;
    private BlockingQueue<Event> preQueue;
    private BlockingQueue<Event> postQueue;
    private Meter meter;
    private Timer timer;
    private boolean processing;

    private ScriptEngineManager engineManager;
    private ScriptEngine engine;
    private CompiledScript script;

    public Filter(FilterConfig config, String threadName) {
        super(threadName);
        processing = false;
        this.config = config;
        if (config.getEnableMeter() != null && config.getEnableMeter()) {
            this.meter = metricRegistry.meter(MetricRegistry.name(this.getClass(), "meter"));
            this.timer = metricRegistry.timer(MetricRegistry.name(this.getClass(), "timer"));
        }

        engineManager = new ScriptEngineManager();
        engine = engineManager.getEngineByName("JavaScript");
        if (config.getCondition() != null) {
            try {
                if (engine instanceof Compilable) {
                    this.script = ((Compilable) engine).compile(config.getCondition());
                }
            } catch (ScriptException e) {
                logger.error("Error compiling script {}.", config.getCondition());
                System.exit(1);
            }
        }
    }

    protected BlockingQueue<Event> getPreQueue() {
        return preQueue;
    }

    protected void setPreQueue(BlockingQueue<Event> preQueue) {
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
        Timer.Context context = null;
        try {
            if (config.getEnableMeter() != null && config.getEnableMeter()) {
                meter.mark();
                context = timer.time();
            }
            Bindings bindings = engine.createBindings();
            bindings.put("event", event);
            boolean result = true;
            if (script != null) {
                result = (boolean) script.eval(bindings);
            } else if (config.getCondition() != null) {
                result = (boolean) engine.eval(config.getCondition(), bindings);
            }
            if (result) {
                event = preProcess(event);
                event = filter(event);
                event = postProcess(event);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (context != null)
                context.stop();
        }
        return event;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Event event = preQueue.take();
                processing = true;
                event = process(event);
                processing = false;
                postQueue.put(event);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void shutdown() {
        while (processing || preQueue.size() > 0) {
            try {
                logger.debug("Processing the remaining events in the tube, please wait a moment...");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
