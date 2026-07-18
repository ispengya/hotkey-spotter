package com.ispengya.hotkey.remoting.client;

import com.ispengya.hotkey.remoting.message.AccessReportMessage;
import com.ispengya.hotkey.remoting.message.HotKeyQueryRequest;
import com.ispengya.hotkey.remoting.message.PushChannelRegisterMessage;
import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;
import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Serializer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * HotKeyRemotingClient 封装了与服务端交互的业务请求逻辑。
 *
 * <p>通过 EventBus 接收推送事件，解耦 Netty 层。</p>
 *
 * @author ispengya
 */
public final class HotKeyRemotingClient {

    private final Serializer serializer;
    private final ClientRequestSender sender;
    private final String appName;
    private final ExecutorService pushExecutor = Executors.newSingleThreadExecutor();

    /**
     * 推送事件回调（由 EventBus 注册）。
     */
    private volatile Consumer<HotKeyViewMessage> pushCallback;

    public HotKeyRemotingClient(Serializer serializer,
                                ClientRequestSender sender,
                                String appName) {
        this.serializer = serializer;
        this.sender = sender;
        this.appName = appName;
        ClientInboundHandler.setPushHandler(this::handlePushCommand);
    }

    /**
     * 设置推送回调（由 HotKeyBootstrap 注册）。
     */
    public void setPushCallback(Consumer<HotKeyViewMessage> callback) {
        this.pushCallback = callback;
    }

    private void handlePushCommand(Command command) {
        if (command.getType() != CommandType.HOT_KEY_PUSH) {
            return;
        }
        Consumer<HotKeyViewMessage> callback = pushCallback;
        if (callback == null) {
            return;
        }
        byte[] payload = command.getPayload();
        pushExecutor.execute(() -> {
            HotKeyViewMessage message = serializer.deserialize(payload, HotKeyViewMessage.class);
            callback.accept(message);
        });
    }

    public void reportAccess(AccessReportMessage message) {
        byte[] bytes = serializer.serialize(message);
        Command command = new Command(CommandType.ACCESS_REPORT, bytes);
        sender.sendOneWay(command);
    }

    public void registerPushChannel() {
        PushChannelRegisterMessage message = new PushChannelRegisterMessage(appName);
        byte[] bytes = serializer.serialize(message);
        Command command = new Command(CommandType.PUSH_CHANNEL_REGISTER, bytes);
        sender.sendOneWay(command);
    }

    public HotKeyViewMessage queryAllHotKeys(Map<String, Long> lastVersions, long timeoutMillis) {
        HotKeyQueryRequest request = new HotKeyQueryRequest(lastVersions);
        byte[] bytes = serializer.serialize(request);
        Command command = new Command(CommandType.HOT_KEY_QUERY, bytes);
        try {
            CompletableFuture<Command> future = sender.sendSync(command, timeoutMillis);
            Command responseCommand = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return serializer.deserialize(responseCommand.getPayload(), HotKeyViewMessage.class);
        } catch (Exception e) {
            HotKeyViewMessage message = new HotKeyViewMessage();
            message.setViews(new HashMap<>());
            return message;
        }
    }
}
