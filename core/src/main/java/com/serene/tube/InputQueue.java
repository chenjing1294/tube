package com.serene.tube;

import java.util.concurrent.ArrayBlockingQueue;

public class InputQueue extends ArrayBlockingQueue<Event> {
    public InputQueue(int capacity) {
        super(capacity);
    }
}
