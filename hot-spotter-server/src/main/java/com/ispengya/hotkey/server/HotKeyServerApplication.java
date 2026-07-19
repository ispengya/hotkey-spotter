package com.ispengya.hotkey.server;

import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.server.core.AppWindowRegistry;
import com.ispengya.hotkey.server.core.RuleManager;
import com.ispengya.hotkey.server.listener.RuleChangeListener;
import com.ispengya.hotkey.server.nacos.NacosServerClient;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Fastjson2Serializer;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import com.ispengya.hotkey.remoting.server.NettyServer;
import com.ispengya.hotkey.remoting.server.NettyServerConfig;
import com.ispengya.hotkey.remoting.server.ServerChannelManager;
import com.ispengya.hotkey.remoting.server.ServiceRegistration;
import com.ispengya.hotkey.server.config.HotKeyServerProperties;
import com.ispengya.hotkey.server.core.AccessReportPipeline;
import com.ispengya.hotkey.server.nacos.NacosIntegration;
import com.ispengya.hotkey.server.remoting.DefaultServerRequestDispatcher;
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

        // Core — EventBus 用于规则变更等事件广播
        EventBus eventBus = new EventBus();

        // RuleManager 是规则配置的统一入口
        RuleManager ruleManager = new RuleManager(algorithmProps.getMinCountThreshold());

        // AppWindowRegistry 按 appName 维度管理每个 key 的独立滑动窗口
        AppWindowRegistry windowRegistry = new AppWindowRegistry(ruleManager);

        Serializer serializer = new Fastjson2Serializer();

        // Pipeline
        HotKeyChangePublisher changePublisher = new HotKeyChangePublisher(channelManager, serializer, properties.isDebugEnabled());
        AccessReportPipeline pipeline = new AccessReportPipeline(
                windowRegistry,
                ruleManager,
                changePublisher
        );

        // Handlers
        dispatcher.registerHandler(CommandType.ACCESS_REPORT, new ReportRequestHandler(serializer, pipeline, channelManager, properties.isDebugEnabled()));
        dispatcher.registerHandler(CommandType.ADMIN_PING, new PingRequestHandler(properties.isDebugEnabled()));

        // Nacos
        NacosServerClient nacosClient = new NacosServerClient(
                properties.getNacos().getServerAddr(),
                properties.getNacos().getNamespace(),
                eventBus
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

        NacosIntegration nacosIntegration = new NacosIntegration(
                nacosClient,
                serviceRegistration,
                ruleManager,
                properties.getRule().getDataId(),
                properties.getRule().getAppNames()
        );

        // 规则变更监听器：从 EventBus 订阅 RuleChangeEvent，更新 RuleManager
        new RuleChangeListener(eventBus, ruleManager);

        // 当 rule.appNames 为空时，启用懒加载：首次遇到新 appName 时自动从 Nacos 拉取规则
        if (properties.getRule().getAppNames().isEmpty()) {
            ruleManager.enableLazyLoading(nacosClient, properties.getRule().getDataId());
        }

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
