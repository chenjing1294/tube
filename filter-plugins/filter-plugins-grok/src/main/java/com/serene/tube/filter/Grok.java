package com.serene.tube.filter;

import com.serene.tube.Event;
import com.serene.tube.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Grok extends Filter {
    private final static Logger logger = LoggerFactory.getLogger(Grok.class);
    private Map<String, String> regExp = new HashMap<>();
    private List<Pattern> matches;
    private Pattern pattern = Pattern.compile("\\%\\{[_0-9a-zA-Z]+(:[_.0-9a-zA-Z]+){0,2}\\}");

    public Grok(GrokConfig config, String threadName) {
        super(config, threadName);
        final String path = "patterns";
        final File jarFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());

        if (jarFile.isFile()) { // Run with JAR file
            try {
                JarFile jar = new JarFile(jarFile);
                final Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    final String name = entries.nextElement().getName();
                    if (name.startsWith(path)) {
                        InputStream in = ClassLoader.getSystemResourceAsStream(name);
                        File file = File.createTempFile(name, "");
                        try {
                            OutputStream os = new FileOutputStream(file);
                            int bytesRead = 0;
                            byte[] buffer = new byte[8192];
                            while ((bytesRead = in.read(buffer, 0, 8192)) != -1) {
                                os.write(buffer, 0, bytesRead);
                            }
                            os.close();
                            in.close();
                        } catch (Exception e) {
                            logger.warn(e.getMessage(), e);
                        }
                        try {
                            loadPatterns(file);
                        } catch (Exception e) {
                            logger.warn(e.getMessage(), e);
                        }
                    }
                }
                jar.close();
            } catch (IOException e) {
                logger.error("failed to prepare patterns");
                logger.trace(e.getMessage(), e);
                System.exit(1);
            }

        } else { // Run with IDE
            try {
                loadPatterns(new File(ClassLoader.getSystemResource(path).getFile()));
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }

        if (((GrokConfig) this.config).getPatternPaths() != null) {
            try {
                List<String> patternPaths = ((GrokConfig) this.config).getPatternPaths();
                for (String patternPath : patternPaths) {
                    loadPatterns(new File(patternPath));
                }
            } catch (Exception e) {
                logger.error("failed to read patternPaths");
                logger.trace(e.getMessage(), e);
                System.exit(1);
            }
        }
        this.matches = new ArrayList<>();

        if (((GrokConfig) this.config).getMatches() != null) {
            List<String> matches = ((GrokConfig) this.config).getMatches();
            for (String match : matches) {
                match = convertPattern(match);
                this.matches.add(Pattern.compile(match));
            }
        } else {
            logger.error("must config matches");
            System.exit(1);
        }
    }

    private String convertPatternOneLevel(String p) {
        Matcher m = pattern.matcher(p);
        StringBuilder newPattern = new StringBuilder();
        int last_end = 0;
        while (m.find()) {
            newPattern.append(p.substring(last_end, m.start()));
            String syntaxANDsemanticANDtype = m.group().substring(2, m.group().length() - 1);
            String syntax, semantic, type;
            String[] syntaxANDsemanticANDtypeArray = syntaxANDsemanticANDtype.split(":", 3);

            syntax = syntaxANDsemanticANDtypeArray[0];

            if (syntaxANDsemanticANDtypeArray.length > 1) {
                semantic = syntaxANDsemanticANDtypeArray[1];
                if (syntaxANDsemanticANDtypeArray.length > 2) {
                    type = syntaxANDsemanticANDtypeArray[2];
                } else {
                    type = "string";
                }
                newPattern.append("(?<").append(semantic).append("0").append(type).append(">").append(regExp.get(syntax)).append(")");
            } else {
                newPattern.append(regExp.get(syntax));
            }
            last_end = m.end();
        }
        newPattern.append(p.substring(last_end));
        return newPattern.toString();
    }

    private String convertPattern(String p) {
        do {
            String rst = this.convertPatternOneLevel(p);
            if (rst.equals(p)) {
                return p;
            }
            p = rst;
        } while (true);
    }

    private void loadPatterns(File path) {
        if (path.isDirectory()) {
            File[] files = path.listFiles();
            if (files != null)
                for (File subpath : files)
                    loadPatterns(subpath);
        } else {
            try {
                BufferedReader br = new BufferedReader(new FileReader(path));
                String sCurrentLine;

                while ((sCurrentLine = br.readLine()) != null) {
                    sCurrentLine = sCurrentLine.trim();
                    if (sCurrentLine.length() == 0 || sCurrentLine.indexOf("#") == 0) {
                        continue;
                    }
                    this.regExp.put(sCurrentLine.split("\\s", 2)[0],
                            sCurrentLine.split("\\s", 2)[1]);
                }
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public Event filter(Event event) {
        String message = (String) event.get("@message");
        if (((GrokConfig) config).getSrc() != null) {
            String src = ((GrokConfig) config).getSrc();
            message = (String) event.get(src);
        }
        for (Pattern pattern : this.matches) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.matches()) {
                Map<String, Integer> namedGroups = Util.namedGroups(pattern);
                namedGroups.forEach((k, v) -> {
                    String[] split = k.split("0");
                    String field = split[0];
                    String type = split[1];
                    Object value = null;
                    switch (type) {
                        case "int":
                            value = Integer.valueOf(matcher.group(k));
                            break;
                        case "float":
                            value = Double.valueOf(matcher.group(k));
                            break;
                        case "string":
                            value = matcher.group(k);
                            break;
                    }
                    event.put(field, value);
                });
                break;
            }
        }
        return event;
    }
}
