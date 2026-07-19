package com.ispengya.hotkey.server.remoting;

import com.ispengya.hotkey.remoting.message.AccessReportMessage;
import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import com.ispengya.hotkey.remoting.server.ServerChannelManager;
import com.ispengya.hotkey.server.core.AccessReportPipeline;
import com.ispengya.hotkey.server.model.AccessReport;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * ReportRequestHandler 处理访问统计上报请求。
 *
 * <p>对应命令类型：{@link CommandType#ACCESS_REPORT}。</p>
 *
 * <p>整体流程：
 * <ol>
 *     <li>解析客户端上报的 {@link AccessReportMessage}</li>
 *     <li>将每个 key 的访问计数封装为 {@link AccessReport}</li>
 *     <li>把 {@link AccessReport} 提交给核心上报管道，由其负责入队与消费</li>
 * </ol>
 * </p>
 */
public final class ReportRequestHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ReportRequestHandler.class);

    private final Serializer serializer;
    private final AccessReportPipeline pipeline;
    private final ServerChannelManager channelManager;
    private final boolean debugEnabled;

    public ReportRequestHandler(Serializer serializer,
                                AccessReportPipeline pipeline,
                                ServerChannelManager channelManager,
                                boolean debugEnabled) {
        this.serializer = serializer;
        this.pipeline = pipeline;
        this.channelManager = channelManager;
        this.debugEnabled = debugEnabled;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Command command) {
        try {
            AccessReportMessage message = serializer.deserialize(command.getPayload(), AccessReportMessage.class);
            if (message == null) {
                return;
            }

            // 收到首次上报时自动将该 channel 注册为推送通道（Plan B：无需独立注册协议）
            String appName = message.getAppName();
            if (appName != null && !appName.isEmpty()) {
                channelManager.registerPushChannel(appName, ctx.channel());
            }

            Map<String, Integer> counts = message.getKeyAccessCounts();
            if (debugEnabled && log.isDebugEnabled()) {
                int size = counts == null ? 0 : counts.size();
                log.debug("Receive access report. appName={}, keyCount={}",
                        message.getAppName(), size);
            }
            if (counts != null) {
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    String key = entry.getKey();
                    Integer count = entry.getValue();
                    if (count == null || count <= 0) {
                        continue;
                    }
                    AccessReport report = new AccessReport(
                            message.getAppName(),
                            key,
                            message.getTimestamp(),
                            true,
                            0L,
                            count
                    );
                    offer(report);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle access report", e);
        }
    }

    private void offer(AccessReport report) {
        pipeline.submit(report);
    }
}
