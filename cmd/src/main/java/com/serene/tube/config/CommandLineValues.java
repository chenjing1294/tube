package com.serene.tube.config;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionHandlerRegistry;

import java.util.Map;

public class CommandLineValues {
    @Option(name = "-h", aliases = {"--help"}, usage = "Print Help Information", help = true)
    private boolean isHelp;

    @Option(name = "-f", aliases = {"--config-file"}, metaVar = "<file>", handler = HangoutConfigHandler.class, usage = "Specify a config file", required = true)
    private Map configFile;

    public CommandLineValues(String... args) {
        CmdLineParser parser = new CmdLineParser(this);
        OptionHandlerRegistry.getRegistry().registerHandler(Map.class, HangoutConfigHandler.class);
        try {
            parser.parseArgument(args);
            if (isHelp) {
                parser.printUsage(System.out);
                System.exit(0);
            }
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            System.exit(1);
        }
    }

    public Map getConfigFile() {
        return configFile;
    }
}
