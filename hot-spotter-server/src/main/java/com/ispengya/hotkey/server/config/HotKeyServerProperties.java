package com.ispengya.hotkey.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class HotKeyServerProperties {

    private static final Logger log = LoggerFactory.getLogger(HotKeyServerProperties.class);
    private static final String DEFAULT_CONFIG_NAME = "hotkey-server.properties";

    private final Server server;
    private final Aggregator aggregator;
    private final Algorithm algorithm;
    private final Scheduler scheduler;
    private final boolean debugEnabled;

    private HotKeyServerProperties(Server server,
                                   Aggregator aggregator,
                                   Algorithm algorithm,
                                   Scheduler scheduler,
                                   boolean debugEnabled) {
        this.server = server;
        this.aggregator = aggregator;
        this.algorithm = algorithm;
        this.scheduler = scheduler;
        this.debugEnabled = debugEnabled;
    }

    public static HotKeyServerProperties load() {
        Properties props = new Properties();
        try (InputStream in = HotKeyServerProperties.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG_NAME)) {
            if (in != null) {
                props.load(in);
            } else {
                log.warn("{} not found on classpath, using defaults", DEFAULT_CONFIG_NAME);
            }
        } catch (IOException e) {
            log.warn("Failed to load {}, using defaults", DEFAULT_CONFIG_NAME, e);
        }

        Server server = new Server(
                getInt(props, "server.port", 8888),
                getInt(props, "server.bossThreads", 1),
                getInt(props, "server.workerThreads", 4),
                getInt(props, "server.backlog", 1024),
                getInt(props, "server.connectTimeoutMillis", 3000),
                getInt(props, "server.readerIdleSeconds", 60),
                getInt(props, "server.writerIdleSeconds", 0),
                getInt(props, "server.allIdleSeconds", 0),
                getInt(props, "server.maxFrameBytes", 1024 * 1024)
        );

        Aggregator aggregator = new Aggregator(
                getLong(props, "aggregator.windowSizeMillis", 1000L),
                getInt(props, "aggregator.windowSlotCount", 30)
        );

        Algorithm algorithm = new Algorithm(
                getLong(props, "algorithm.minCountThreshold", 3L)
        );

        Scheduler scheduler = new Scheduler(
                getInt(props, "scheduler.corePoolSize", 1),
                getInt(props, "scheduler.workerPoolSize", 4),
                getLong(props, "scheduler.periodMillis", 1000L),
                getLong(props, "scheduler.decayPeriodMillis", 1000L),
                getLong(props, "scheduler.hotKeyIdleMillis", 60000L)
        );

        boolean debugEnabled = getBoolean(props, "logging.debugEnabled", false);

        return new HotKeyServerProperties(server, aggregator, algorithm, scheduler, debugEnabled);
    }

    public Server getServer() {
        return server;
    }

    public Aggregator getAggregator() {
        return aggregator;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    private static int getInt(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid int for key {}, value {}. Using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static long getLong(Properties props, String key, long defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid long for key {}, value {}. Using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static boolean getBoolean(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "no".equals(normalized) || "off".equals(normalized)) {
            return false;
        }
        log.warn("Invalid boolean for key {}, value {}. Using default {}", key, value, defaultValue);
        return defaultValue;
    }

    public static final class Server {

        private final int port;
        private final int bossThreads;
        private final int workerThreads;
        private final int backlog;
        private final int connectTimeoutMillis;
        private final int readerIdleSeconds;
        private final int writerIdleSeconds;
        private final int allIdleSeconds;
        private final int maxFrameBytes;

        public Server(int port,
                      int bossThreads,
                      int workerThreads,
                      int backlog,
                      int connectTimeoutMillis,
                      int readerIdleSeconds,
                      int writerIdleSeconds,
                      int allIdleSeconds,
                      int maxFrameBytes) {
            this.port = port;
            this.bossThreads = bossThreads;
            this.workerThreads = workerThreads;
            this.backlog = backlog;
            this.connectTimeoutMillis = connectTimeoutMillis;
            this.readerIdleSeconds = readerIdleSeconds;
            this.writerIdleSeconds = writerIdleSeconds;
            this.allIdleSeconds = allIdleSeconds;
            this.maxFrameBytes = maxFrameBytes;
        }

        public int getPort() {
            return port;
        }

        public int getBossThreads() {
            return bossThreads;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public int getBacklog() {
            return backlog;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public int getReaderIdleSeconds() {
            return readerIdleSeconds;
        }

        public int getWriterIdleSeconds() {
            return writerIdleSeconds;
        }

        public int getAllIdleSeconds() {
            return allIdleSeconds;
        }

        public int getMaxFrameBytes() {
            return maxFrameBytes;
        }
    }

    public static final class Aggregator {

        private final long windowSizeMillis;
        private final int windowSlotCount;

        public Aggregator(long windowSizeMillis, int windowSlotCount) {
            this.windowSizeMillis = windowSizeMillis;
            this.windowSlotCount = windowSlotCount;
        }

        public long getWindowSizeMillis() {
            return windowSizeMillis;
        }

        public int getWindowSlotCount() {
            return windowSlotCount;
        }
    }

    public static final class Algorithm {

        private final long minCountThreshold;

        public Algorithm(long minCountThreshold) {
            this.minCountThreshold = minCountThreshold;
        }

        public long getMinCountThreshold() {
            return minCountThreshold;
        }
    }

    public static final class Scheduler {

        private final int corePoolSize;
        private final int workerPoolSize;
        private final long periodMillis;
        private final long decayPeriodMillis;
        private final long hotKeyIdleMillis;

        public Scheduler(int corePoolSize,
                         int workerPoolSize,
                         long periodMillis,
                         long decayPeriodMillis,
                         long hotKeyIdleMillis) {
            this.corePoolSize = corePoolSize;
            this.workerPoolSize = workerPoolSize;
            this.periodMillis = periodMillis;
            this.decayPeriodMillis = decayPeriodMillis;
            this.hotKeyIdleMillis = hotKeyIdleMillis;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getWorkerPoolSize() {
            return workerPoolSize;
        }

        public long getPeriodMillis() {
            return periodMillis;
        }

        public long getDecayPeriodMillis() {
            return decayPeriodMillis;
        }

        public long getHotKeyIdleMillis() {
            return hotKeyIdleMillis;
        }
    }
}
