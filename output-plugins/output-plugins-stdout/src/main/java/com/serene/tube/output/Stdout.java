package com.serene.tube.output;

import com.serene.tube.Event;
import com.serene.tube.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Stdout extends Output {
    private static final Logger logger = LoggerFactory.getLogger(Stdout.class);


    public Stdout(StdoutConfig config) {
        super(config);
    }

    @Override
    protected void emit(Event event) {
        System.out.println(event);
    }
}
