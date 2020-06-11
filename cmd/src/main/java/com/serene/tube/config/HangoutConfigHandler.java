package com.serene.tube.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

/**
 * @author 陈敬
 * @since 2020年6月5日
 */
public class HangoutConfigHandler extends OneArgumentOptionHandler<Map> {
    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";
    private final static Logger logger = LoggerFactory.getLogger(HangoutConfigHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HangoutConfigHandler(CmdLineParser parser, OptionDef option, Setter<? super Map> setter) {
        super(parser, option, setter);
    }

    @Override
    protected Map parse(String argument) throws CmdLineException {
        InputStream is;
        try {
            if (argument.startsWith(HTTP) || argument.startsWith(HTTPS)) {
                URL httpUrl;
                URLConnection connection;
                httpUrl = new URL(argument);
                connection = httpUrl.openConnection();
                connection.connect();
                is = connection.getInputStream();
            } else {
                is = new FileInputStream(new File(argument));
            }
        } catch (Exception e) {
            throw new CmdLineException(this.owner, e.getMessage(), e);
        }
        Map config = null;
        try {
            config = objectMapper.readValue(is, Map.class);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            System.exit(1);
        }
        if (config.get("inputs") == null || config.get("outputs") == null) {
            logger.error("Error: No inputs or outputs!");
            System.exit(1);
        }
        return config;
    }
}