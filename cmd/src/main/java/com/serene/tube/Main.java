package com.serene.tube;

import com.codahale.metrics.Slf4jReporter;
import com.serene.tube.config.CommandLineValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        //Parse CommandLine arguments
        CommandLineValues cm = new CommandLineValues(args);
        //parse configure file
        Map configs = cm.getConfigFile();

        final List<HashMap<String, Map>> inputConfigs = (ArrayList<HashMap<String, Map>>) configs.get("inputs");
        final List<HashMap<String, Map>> filterConfigs = (ArrayList<HashMap<String, Map>>) configs.get("filters");
        final List<HashMap<String, Map>> outputConfigs = (ArrayList<HashMap<String, Map>>) configs.get("outputs");

        initMetrics();
        new TopologyBuilder(inputConfigs, filterConfigs, outputConfigs).buildTopology();
    }


    private static void initMetrics() {
        String loggerName = "com.serene.tube.metricLogger";
        final Slf4jReporter slf4jReporter = Slf4jReporter
                .forRegistry(Plugin.metricRegistry)
                .outputTo(LoggerFactory.getLogger(loggerName))
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        slf4jReporter.start(100, TimeUnit.SECONDS);
    }
}
