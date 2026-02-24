package com.ispengya.hotkey.remoting.server;

import com.ispengya.hotkey.remoting.protocol.Command;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerChannelManager {

    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private final Map<String, Set<Channel>> appChannels = new ConcurrentHashMap<>();

    private final Map<Channel, String> channelIps = new ConcurrentHashMap<>();

    private final Set<Channel> pushChannels = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void register(Channel channel) {
        channels.add(channel);
    }

    public void unregister(Channel channel) {
        channels.remove(channel);
        channelIps.remove(channel);
        for (Set<Channel> set : appChannels.values()) {
            set.remove(channel);
        }
        pushChannels.remove(channel);
    }

    public void registerPushChannel(String appName, Channel channel) {
        if (channel == null) {
            return;
        }
        pushChannels.add(channel);
        if (appName != null && !appName.isEmpty()) {
            appChannels
                    .computeIfAbsent(appName, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                    .add(channel);
        }
        String ip = extractIp(channel);
        if (ip != null) {
            channelIps.put(channel, ip);
        }
    }

    public void broadcastOnPushChannels(Command command) {
        if (command == null) {
            return;
        }
        for (Channel channel : pushChannels) {
            if (channel.isActive()) {
                channel.writeAndFlush(command);
            }
        }
    }

    public void broadcastToApp(String appName, Command command) {
        if (appName == null || command == null) {
            return;
        }
        Set<Channel> set = appChannels.get(appName);
        if (set == null) {
            return;
        }
        for (Channel channel : set) {
            if (channel.isActive()) {
                channel.writeAndFlush(command);
            }
        }
    }

    private String extractIp(Channel channel) {
        SocketAddress remote = channel.remoteAddress();
        if (remote instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) remote;
            if (inet.getAddress() != null) {
                return inet.getAddress().getHostAddress();
            }
            return inet.getHostString();
        }
        return null;
    }
}
