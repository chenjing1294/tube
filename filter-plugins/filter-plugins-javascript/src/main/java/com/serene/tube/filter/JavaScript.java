package com.serene.tube.filter;

import com.serene.tube.Event;
import com.serene.tube.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class JavaScript extends Filter {
    private final Logger logger = LoggerFactory.getLogger(JavaScript.class);
    private ScriptEngineManager engineManager;
    private ScriptEngine engine;
    private FileReader fileReader;
    private CompiledScript script;

    public JavaScript(JavaScriptConfig config, String threadName) {
        super(config, threadName);
        engineManager = new ScriptEngineManager();
        engine = engineManager.getEngineByName("JavaScript");
        String customFunctionPath = config.getCustomFunctionPath();
        try {
            fileReader = new FileReader(customFunctionPath);
            if (engine instanceof Compilable) {
                this.script = ((Compilable) engine).compile(fileReader);
            }
        } catch (FileNotFoundException | ScriptException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public Event filter(Event event) {
        if (fileReader != null) {
            try {
                if (script != null) {
                    script.eval();
                } else {
                    engine.eval(fileReader);
                }
                CustomFunction customFunction = ((Invocable) engine).getInterface(CustomFunction.class);
                event = customFunction.filter(event);
            } catch (ScriptException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return event;
    }
}
