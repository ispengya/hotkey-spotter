package com.ispengya.hotkey.server;

import com.ispengya.hotkey.server.nacos.NacosServerClient;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Fastjson2Serializer;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import com.ispengya.hotkey.remoting.server.NettyServer;
import com.ispengya.hotkey.remoting.server.NettyServerConfig;
import com.ispengya.hotkey.remoting.server.ServerChannelManager;
import com.ispengya.hotkey.remoting.server.ServiceRegistration;
import com.ispengya.hotkey.server.config.HotKeyServerProperties;
import com.ispengya.hotkey.server.core.InstanceWindowRegistry;
import com.ispengya.hotkey.server.core.InMemoryHotKeyResultStore;
import com.ispengya.hotkey.server.core.HotKeyComputeAlgorithm;
import com.ispengya.hotkey.server.core.AccessReportPipeline;
import com.ispengya.hotkey.server.nacos.NacosIntegration;
import com.ispengya.hotkey.server.remoting.DefaultServerRequestDispatcher;
import com.ispengya.hotkey.server.remoting.HotKeyQueryHandler;
import com.ispengya.hotkey.server.remoting.PingRequestHandler;
import com.ispengya.hotkey.server.remoting.ReportRequestHandler;
import com.ispengya.hotkey.server.scheduler.HotKeyChangePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

/**
 * HotKeyServerApplication 服务端入口程序。
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

        // Netty config
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

        // Remoting
        ServerChannelManager channelManager = new ServerChannelManager();
        DefaultServerRequestDispatcher dispatcher = new DefaultServerRequestDispatcher();
        NettyServer nettyServer = new NettyServer(serverConfig, channelManager, dispatcher);

        // Core
        InstanceWindowRegistry windowRegistry = new InstanceWindowRegistry(
                windowRegistryProps.getWindowSizeMillis(),
                windowRegistryProps.getWindowSlotCount()
        );
        InMemoryHotKeyResultStore resultStore = new InMemoryHotKeyResultStore();
        HotKeyComputeAlgorithm algorithm = new HotKeyComputeAlgorithm(
                algorithmProps.getMinCountThreshold()
        );

        Serializer serializer = new Fastjson2Serializer();

        // Pipeline
        HotKeyChangePublisher changePublisher = new HotKeyChangePublisher(channelManager, serializer, properties.isDebugEnabled());
        AccessReportPipeline pipeline = new AccessReportPipeline(
                windowRegistry,
                algorithm,
                resultStore,
                changePublisher
        );

        // Handlers
        dispatcher.registerHandler(CommandType.ACCESS_REPORT, new ReportRequestHandler(serializer, pipeline, channelManager, properties.isDebugEnabled()));
        dispatcher.registerHandler(CommandType.HOT_KEY_QUERY, new HotKeyQueryHandler(resultStore, serializer, properties.isDebugEnabled()));
        dispatcher.registerHandler(CommandType.ADMIN_PING, new PingRequestHandler(properties.isDebugEnabled()));

        // Nacos
        NacosServerClient nacosClient = new NacosServerClient(
                properties.getNacos().getServerAddr(),
                properties.getNacos().getNamespace()
        );
        try {
            nacosClient.init();
        } catch (Exception e) {
            log.error("Failed to initialize nacos client", e);
            throw new RuntimeException("Failed to initialize nacos client", e);
        }

        String ip = getLocalIp();
        int port = serverProps.getPort();
        ServiceRegistration serviceRegistration = new ServiceRegistration(
                nacosClient,
                properties.getNacos().getServiceName(),
                ip,
                port
        );

        NacosIntegration nacosIntegration = new NacosIntegration(nacosClient, serviceRegistration);
        nacosIntegration.setRuleChangeListener((appName, ruleConfig) -> {
            algorithm.updateRuleConfig(ruleConfig);
            log.info("Rule config updated for app: {}", appName);
        });

        // Bootstrap
        HotKeyServerBootstrap bootstrap = new HotKeyServerBootstrap(nettyServer, nacosIntegration);
        bootstrap.start();

        log.info("HotKey Server started on port {}", serverConfig.getPort());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Stopping HotKey Server...");
            bootstrap.stop();
            log.info("HotKey Server stopped.");
        }));
    }

    private static String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("Failed to get local IP, using 127.0.0.1", e);
            return "127.0.0.1";
        }
    }
}
