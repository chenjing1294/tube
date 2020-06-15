package com.serene.tube;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Input的子类负责覆盖{@link #run()}方法，在该方法内部从某个源获取原始字符串数据
 * 后调用{@link Input#process(String)}方法
 *
 * @author 陈敬
 * @since 2020年6月07日
 */
public abstract class Input extends Thread implements Plugin {
    private final static Logger logger = LoggerFactory.getLogger(Input.class);
    protected final InputConfig config;
    private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private Meter meter;
    private InputQueue inputQueue;
    private ObjectMapper objectMapper = new ObjectMapper();

    public Input(InputConfig config, String threadName) {
        super(threadName);
        registerShutdownHook();
        this.config = config;
        if (config.getEnableMeter() != null && config.getEnableMeter()) {
            this.meter = metricRegistry.meter(MetricRegistry.name(this.getClass()));
        }
        if (config.getCodec() == null) {
            logger.error("[codec] properties must be configured");
            System.exit(1);
        }
    }

    void setInputQueue(InputQueue inputQueue) {
        this.inputQueue = inputQueue;
    }

    /**
     * 在进去过滤器（如果有的话）之前进行预处理；或者在没有过滤器的情况下，在进入output之前进行预处理
     */
    protected Event beforeEnterInputQueue(Event event) {
        if (config.getType() != null) {
            event.put("type", config.getType());
        }
        return event;
    }

    /**
     * 将原始的字符串预处理成{@link Event}对象，并放入{@link InputQueue}
     *
     * @param message 原始字符串
     */
    protected void process(String message) {
        Event event = new Event();
        switch (config.getCodec()) {
            case "json":
                try {
                    message = message.replaceAll("\\\\(?!\")", "\\\\\\\\");
                    event = objectMapper.readValue(message, Event.class);
                } catch (JsonParseException e) {
                    logger.error("it seems that underlying input contains invalid content.", e);
                    return;
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    return;
                }
                break;
            case "plain":
                break;
            default:
                logger.error("unsupported codec [{}]", config.getCodec());
                return;
        }
        event.put("@message", message);
        event.put("@timestamp", df.format(new Date()));
        beforeEnterInputQueue(event);
        try {
            inputQueue.put(event);
            if (config.getEnableMeter() != null && config.getEnableMeter()) {
                meter.mark();
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
