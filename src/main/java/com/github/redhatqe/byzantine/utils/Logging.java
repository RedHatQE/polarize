package com.github.redhatqe.byzantine.utils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.config.yaml.YamlConfigurationFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Logging {
    public String ctxName;
    public Map<String, String> patterns;
    public FileAppender fileApp;
    public ConsoleAppender conApp;
    public String filename;
    public Level level;
    public LoggerContext context;
    public Configuration cfg;

    public Logging(String name, String filename)  {
        this.ctxName = name;
        this.patterns = defaultPatterns();
        this.filename = filename;
        this.level = Level.ERROR;

        ConfigurationFactory factory = YamlConfigurationFactory.getInstance();
        FileInputStream fis = null;
        try {
            URL p = Logging.class.getClassLoader().getResource("log4j2-test.yaml");
            String path = "";
            if (p != null)
                path = p.getPath();
            fis = new FileInputStream(new File(path));
            ConfigurationSource cfgSrc = new ConfigurationSource(fis);
            this.context = new LoggerContext(this.ctxName);
            cfg = factory.getConfiguration(this.context, cfgSrc);

            PatternLayout fileLO = makePattern(this.patterns.get("file"), cfg);
            PatternLayout conLO = makePattern(this.patterns.get("console"), cfg);

            this.conApp = makeConsoleAppender(conLO, this.ctxName + "_OUT");
            cfg.addAppender(this.conApp);

            this.fileApp = Logging.makeFileAppender(this.filename, fileLO, this.ctxName + "_FILE");
            cfg.addAppender(this.fileApp);

            LoggerConfig logConfig = new LoggerConfig(this.ctxName, this.level, false);
            logConfig.addAppender(this.conApp, Level.INFO, null);
            logConfig.addAppender(this.fileApp, Level.DEBUG, null);
            cfg.addLogger(this.ctxName, logConfig);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Logger getLogger() {
        if(!this.context.isStarted())
            this.context.start(this.cfg);
        Collection<org.apache.logging.log4j.core.Logger> loggers = this.context.getLoggers();
        List<String> lnames = this.context.getLoggers().stream()
                .map(l -> {
                    String name = l.getName();
                    System.out.println(name);
                    return name;
                })
                .collect(Collectors.toList());
        Logger log = this.context.getLogger(this.ctxName);
        return log;
    }

    public static Map<String, String> defaultPatterns() {
        Map<String, String> ptns = new HashMap<>();
        ptns.put("console", "%m%n");
        ptns.put("file", "%d %p %C{1.} [%t] %m%n");
        return ptns;
    }

    public static PatternLayout makePattern(String pattern, Configuration cfg) {
        return PatternLayout.newBuilder()
                .withCharset(Charset.forName("UTF-8"))
                .withPattern(pattern)
                .withConfiguration(cfg)
                .build();
    }

    /**
     * Makes a FileAppender with the most common options
     *
     * @param filename
     * @param layout
     * @param name
     * @return
     */
    public static FileAppender makeFileAppender(String filename, PatternLayout layout, String name) {
        return FileAppender.newBuilder()
                .withFileName(filename)
                .withBufferedIo(true)
                .withLayout(layout)
                .withName(name)
                .build();
    }

    public static ConsoleAppender makeConsoleAppender(PatternLayout layout, String name) {
        return ConsoleAppender.newBuilder()
                .withLayout(layout)
                .withBufferedIo(true)
                .withName(name)
                .build();
    }

    public static void main(String[] args) {
        Logger logger = LogManager.getLogger(Logging.class.getSimpleName());
        logger.info("This should show in console and file");
        logger.debug("This should only show in file");
        logger.info(Logging.class.getSimpleName());
    }
}
