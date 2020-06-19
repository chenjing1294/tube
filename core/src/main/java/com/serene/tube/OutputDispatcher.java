package com.serene.tube;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class OutputDispatcher extends Thread implements Plugin {
    private static final Logger logger = LoggerFactory.getLogger(OutputDispatcher.class);
    private List<Output> outputs;
    private OutputQueue outputQueue;


    public OutputDispatcher(List<Output> outputs) {
        super(OutputDispatcher.class.getSimpleName());
        this.outputs = outputs;
    }

    public void setOutputQueue(OutputQueue outputQueue) {
        this.outputQueue = outputQueue;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Event event = outputQueue.take();
                for (Output output : outputs) {
                    output.process(event);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void shutdown() {
        while (outputQueue.size() > 0) {
            try {
                logger.debug("Processing the remaining events in the tube, please wait a moment...");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
