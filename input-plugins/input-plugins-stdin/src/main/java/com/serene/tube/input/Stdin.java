package com.serene.tube.input;

import com.serene.tube.Event;
import com.serene.tube.Input;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Stdin extends Input {
    private static final Logger logger = LoggerFactory.getLogger(Stdin.class);
    private String hostname;

    public Stdin(StdinConfig config, String threadName) {
        super(config, threadName);
        if (config.getHostname()) {
            try {
                this.hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    @Override
    protected Event beforeEnterInputQueue(Event event) {
        event = super.beforeEnterInputQueue(event);
        if (((StdinConfig) config).getHostname()) {
            event.put("hostname", hostname);
        }
        return event;
    }

    @Override
    public void run() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String input;
            while ((input = br.readLine()) != null) {
                process(input);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
