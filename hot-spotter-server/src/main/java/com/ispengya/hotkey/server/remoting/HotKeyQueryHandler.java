package com.ispengya.hotkey.server.remoting;

import com.ispengya.hotkey.remoting.message.HotKeyQueryRequest;
import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;
import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import com.ispengya.hotkey.server.core.HotKeyResultStore;
import com.ispengya.hotkey.server.model.HotKeyResult;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * HotKeyQueryHandler 处理热 Key 查询请求。
 *
 * <p>对应命令类型：{@link CommandType#HOT_KEY_QUERY}。
 * 根据实例 ID 和版本号返回最新的热 Key 视图。</p>
 *
 * @author ispengya
 */
public final class HotKeyQueryHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(HotKeyQueryHandler.class);

    private final HotKeyResultStore resultStore;
    private final Serializer serializer;

    /**
     * 构造查询请求处理器。
     *
     * @param resultStore 热 Key 结果存储
     * @param serializer  序列化器
     */
    public HotKeyQueryHandler(HotKeyResultStore resultStore,
                              Serializer serializer) {
        this.resultStore = resultStore;
        this.serializer = serializer;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Command command) {
        try {
            HotKeyQueryRequest request = serializer.deserialize(command.getPayload(), HotKeyQueryRequest.class);
            if (request == null) {
                return;
            }

            Map<String, Long> lastVersions = request.getLastVersions();

            HotKeyViewMessage responseMsg;
            Map<String, HotKeyViewMessage.ViewEntry> views = new HashMap<>();
            for (HotKeyResult result : resultStore.listAll()) {
                String owner = result.getAppName();
                long clientVersion = 0L;
                if (lastVersions != null) {
                    Long v = lastVersions.get(owner);
                    if (v != null) {
                        clientVersion = v;
                    }
                }
                if (result.getVersion() <= clientVersion) {
                    continue;
                }

                HotKeyViewMessage.ViewEntry entry = new HotKeyViewMessage.ViewEntry(
                        result.getVersion(),
                        result.getHotKeys()
                );
                views.put(owner, entry);
            }
            responseMsg = new HotKeyViewMessage();
            responseMsg.setViews(views);

            byte[] payload = serializer.serialize(responseMsg);
            Command response = new Command(CommandType.HOT_KEY_QUERY, command.getRequestId(), payload);
            ctx.writeAndFlush(response);

        } catch (Exception e) {
            log.error("Failed to handle hot key query", e);
        }
    }
}
