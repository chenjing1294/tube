package com.serene.tube.filter;

import com.serene.tube.Event;
import com.serene.tube.Filter;

import java.util.List;

public class Remove extends Filter {
    public Remove(RemoveConfig config, String threadName) {
        super(config, threadName);
    }

    @Override
    public Event filter(Event event) {
        List<String> fields = ((RemoveConfig) config).getFields();
        if (fields != null) {
            for (String field : fields) {
                event.remove(field);
            }
        }
        return event;
    }
}
