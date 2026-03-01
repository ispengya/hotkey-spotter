package com.ispengya.hotkey.server;

import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Fastjson2Serializer;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import com.ispengya.hotkey.remoting.server.NettyServer;
import com.ispengya.hotkey.remoting.server.NettyServerConfig;
import com.ispengya.hotkey.remoting.server.ServerChannelManager;
import com.ispengya.hotkey.server.config.HotKeyServerProperties;
import com.ispengya.hotkey.server.core.InstanceWindowRegistry;
import com.ispengya.hotkey.server.core.InMemoryHotKeyResultStore;
import com.ispengya.hotkey.server.core.HotKeyComputeAlgorithm;
import com.ispengya.hotkey.server.core.AccessReportPipeline;
import com.ispengya.hotkey.server.remoting.DefaultServerRequestDispatcher;
import com.ispengya.hotkey.server.remoting.HotKeyQueryHandler;
import com.ispengya.hotkey.server.remoting.PingRequestHandler;
import com.ispengya.hotkey.server.remoting.PushChannelRegisterHandler;
import com.ispengya.hotkey.server.remoting.ReportRequestHandler;
import com.ispengya.hotkey.server.scheduler.HotKeyScheduler;
import com.ispengya.hotkey.server.scheduler.HotKeyComputeTask;
import com.ispengya.hotkey.server.scheduler.HotKeyChangePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * HotKeyServerApplication 服务端入口程序。
 *
 * <p>组装配置、Netty 组件、核心聚合计算组件，并启动服务。</p>
 *
 * @author ispengya
 */
public class HotKeyServerApplication {

    private static final Logger log = LoggerFactory.getLogger(HotKeyServerApplication.class);

    public static void main(String[] args) {
        log.info("Starting HotKey Server...");

        HotKeyServerProperties properties = HotKeyServerProperties.load();
        HotKeyServerProperties.Server serverProps = properties.getServer();
        HotKeyServerProperties.Aggregator windowRegistryProps = properties.getAggregator();
        HotKeyServerProperties.Algorithm algorithmProps = properties.getAlgorithm();
        HotKeyServerProperties.Scheduler schedulerProps = properties.getScheduler();
        NettyServerConfig serverConfig = new NettyServerConfig(
                serverProps.getPort(),
                serverProps.getBossThreads(),
                serverProps.getWorkerThreads(),
                serverProps.getBacklog(),
                serverProps.getConnectTimeoutMillis(),
                serverProps.getReaderIdleSeconds(),
                serverProps.getWriterIdleSeconds(),
                serverProps.getAllIdleSeconds(),
                serverProps.getMaxFrameBytes()
        );

        // 2. Remoting components
        ServerChannelManager channelManager = new ServerChannelManager();
        DefaultServerRequestDispatcher dispatcher = new DefaultServerRequestDispatcher();
        NettyServer nettyServer = new NettyServer(serverConfig, channelManager, dispatcher);

        InstanceWindowRegistry windowRegistry = new InstanceWindowRegistry(
                windowRegistryProps.getWindowSizeMillis(),
                windowRegistryProps.getWindowSlotCount()
        );
        InMemoryHotKeyResultStore resultStore = new InMemoryHotKeyResultStore();

        HotKeyComputeAlgorithm algorithm = new HotKeyComputeAlgorithm(
                algorithmProps.getMinCountThreshold()
        );

        HotKeyComputeTask.setDebugEnabled(properties.isDebugEnabled());

        Serializer serializer = new Fastjson2Serializer();
        HotKeyChangePublisher changePublisher = new HotKeyChangePublisher(channelManager, serializer, properties.isDebugEnabled());
        AccessReportPipeline pipeline = new AccessReportPipeline(
                windowRegistry,
                algorithm,
                resultStore,
                changePublisher
        );

        dispatcher.registerHandler(CommandType.ACCESS_REPORT, new ReportRequestHandler(serializer, pipeline, properties.isDebugEnabled()));
        dispatcher.registerHandler(CommandType.HOT_KEY_QUERY, new HotKeyQueryHandler(resultStore, serializer, properties.isDebugEnabled()));
        dispatcher.registerHandler(CommandType.ADMIN_PING, new PingRequestHandler(properties.isDebugEnabled()));
        dispatcher.registerHandler(CommandType.PUSH_CHANNEL_REGISTER, new PushChannelRegisterHandler(channelManager, serializer));

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
                schedulerProps.getCorePoolSize()
        );
        ExecutorService workerPool = Executors.newFixedThreadPool(
                schedulerProps.getWorkerPoolSize()
        );
        HotKeyScheduler hotKeyScheduler = new HotKeyScheduler(
                scheduler,
                workerPool,
                windowRegistry,
                algorithm,
                resultStore,
                schedulerProps.getPeriodMillis(),
                changePublisher,
                schedulerProps.getDecayPeriodMillis(),
                schedulerProps.getHotKeyIdleMillis(),
                properties.isDebugEnabled()
        );

        // 6. Bootstrap
        HotKeyServerBootstrap bootstrap = new HotKeyServerBootstrap(nettyServer, hotKeyScheduler);
        bootstrap.start();

        log.info("HotKey Server started on port {}", serverConfig.getPort());

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Stopping HotKey Server...");
            bootstrap.stop();
            log.info("HotKey Server stopped.");
        }));
    }
}
