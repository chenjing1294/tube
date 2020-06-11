package com.serene.tube;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class OutputDispatcher extends Thread {
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
}
