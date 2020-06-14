package com.serene.tube.output;

import com.serene.tube.Event;
import com.serene.tube.Output;

public class Blackhole extends Output {
    public Blackhole(BlackholeConfig config) {
        super(config);
    }

    @Override
    protected void emit(Event event) {
        //nothing
    }
}
