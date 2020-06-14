package com.serene.tube;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serene.tube.util.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

public class TopologyBuilder {
    private final Logger logger = LoggerFactory.getLogger(TopologyBuilder.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<HashMap<String, Map>> inputConfigs;
    private final List<HashMap<String, Map>> filterConfigs;
    private final List<HashMap<String, Map>> outputConfigs;

    public TopologyBuilder(List<HashMap<String, Map>> inputConfigs,
                           List<HashMap<String, Map>> filterConfigs,
                           List<HashMap<String, Map>> outputConfigs) {
        this.inputConfigs = inputConfigs;
        this.filterConfigs = filterConfigs;
        this.outputConfigs = outputConfigs;
    }

    private List<Input> buildInputs() {
        List<Input> inputs = new ArrayList<>(inputConfigs.size());
        inputConfigs.forEach(
                input -> {
                    input.forEach((inputType, inputConfig) -> {
                        Class<?> inputClass, inputClassConfig;
                        String inputClassName = "com.serene.tube.input." + inputType;
                        String inputConfigClassName = "com.serene.tube.input." + inputType + "Config";
                        try {
                            inputClass = Class.forName(inputClassName);
                            inputClassConfig = Class.forName(inputConfigClassName);
                            Constructor<?> ctor = inputClass.getConstructor(inputClassConfig, String.class);
                            Config config = objectMapper.readValue(objectMapper.writeValueAsString(inputConfig), A.getJavaType(inputClassConfig));
                            Input inputInstance = (Input) ctor.newInstance(config, "input-" + inputType);
                            logger.info("build input\t [{}] done", inputType);
                            inputs.add(inputInstance);
                        } catch (ClassNotFoundException e) {
                            logger.error("can not find input plugin [{}]", inputType);
                            System.exit(1);
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            System.exit(1);
                        }
                    });
                });
        return inputs;
    }

    private List<Filter> buildFilters() {
        if (filterConfigs == null) {
            return new ArrayList<>(0);
        }
        final List<Filter> filters = new ArrayList<>(filterConfigs.size());
        filterConfigs.forEach(
                filter -> {
                    filter.forEach((filterType, filterConfig) -> {
                        Class<?> filterClass, filterConfigClass;
                        String filterClassName = "com.serene.tube.filter." + filterType;
                        String filterClassConfigName = "com.serene.tube.filter." + filterType + "Config";
                        try {
                            filterClass = Class.forName(filterClassName);
                            filterConfigClass = Class.forName(filterClassConfigName);
                            Constructor<?> ctor = filterClass.getConstructor(filterConfigClass, String.class);
                            Config config = objectMapper.readValue(objectMapper.writeValueAsString(filterConfig), A.getJavaType(filterConfigClass));
                            Filter inputInstance = (Filter) ctor.newInstance(config, "filter-" + filterType);
                            logger.info("build filter\t [{}] done", filterType);
                            filters.add(inputInstance);
                        } catch (ClassNotFoundException e) {
                            logger.error("can not find filter plugin [{}]", filterType);
                            System.exit(1);
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            System.exit(1);
                        }
                    });
                });
        return filters;
    }

    private List<Output> buildOutputs() {
        final List<Output> outputs = new ArrayList<>(outputConfigs.size());
        outputConfigs.forEach(
                output -> {
                    output.forEach((outputType, outputConfig) -> {
                        Class<?> outputClass, outputConfigClass;
                        String outputClassName = "com.serene.tube.output." + outputType;
                        String outputClassConfigName = "com.serene.tube.output." + outputType + "Config";
                        try {
                            outputClass = Class.forName(outputClassName);
                            outputConfigClass = Class.forName(outputClassConfigName);
                            Constructor<?> ctor = outputClass.getConstructor(outputConfigClass);
                            Config config = objectMapper.readValue(objectMapper.writeValueAsString(outputConfig), A.getJavaType(outputConfigClass));
                            Output inputInstance = (Output) ctor.newInstance(config);
                            logger.info("build output\t [{}] done", outputType);
                            outputs.add(inputInstance);
                        } catch (ClassNotFoundException e) {
                            logger.error("can not find output plugin [{}]", outputType);
                            System.exit(1);
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            System.exit(1);
                        }
                    });
                });
        return outputs;
    }

    public void buildTopology() {
        InputQueue inputQueue = new InputQueue(10000);
        OutputQueue outputQueue = new OutputQueue(10000);
        List<Input> inputs = this.buildInputs();
        List<Filter> filters = this.buildFilters();
        List<Output> outputs = this.buildOutputs();
        for (Input input : inputs) {
            input.setInputQueue(inputQueue);
            input.start();
        }
        if (filters.size() > 0) {
            filters.get(0).setPreQueue(inputQueue);
            filters.get(filters.size() - 1).setPostQueue(outputQueue);
        }
        if (filters.size() > 1) {
            for (int i = 0; i < filters.size() - 1; i++) {
                ArrayBlockingQueue<Event> queue = new ArrayBlockingQueue<>(200);
                filters.get(i).setPostQueue(queue);
                filters.get(i + 1).setPreQueue(queue);
            }
        }
        for (Filter filter : filters) {
            filter.start();
        }
        OutputDispatcher outputDispatcher = new OutputDispatcher(outputs);
        outputDispatcher.setOutputQueue(outputQueue);
        outputDispatcher.start();
        logger.info("tube starting success! ({})", "1.1");
    }
}
