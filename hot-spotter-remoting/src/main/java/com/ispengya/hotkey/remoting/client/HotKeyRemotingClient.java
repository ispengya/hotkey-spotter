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
import java.util.concurrent.TimeUnit;

/**
 * HotKeyRemotingClient 封装了与服务端交互的业务请求逻辑。
 *
 * @author ispengya
 */
public final class HotKeyRemotingClient {

    private final Serializer serializer;
    private final ClientRequestSender sender;
    private final String appName;

    public HotKeyRemotingClient(Serializer serializer,
                                ClientRequestSender sender,
                                String appName) {
        this.serializer = serializer;
        this.sender = sender;
        this.appName = appName;
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
