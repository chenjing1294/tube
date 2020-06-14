package com.serene.tube;

import java.util.concurrent.ArrayBlockingQueue;

public class OutputQueue extends ArrayBlockingQueue<Event> {
    public OutputQueue(int capacity) {
        super(capacity);
    }
}
