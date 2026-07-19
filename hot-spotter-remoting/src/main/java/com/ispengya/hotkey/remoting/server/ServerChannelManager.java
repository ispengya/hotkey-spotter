package com.ispengya.hotkey.remoting.server;

import com.ispengya.hotkey.remoting.protocol.Command;
import io.netty.channel.Channel;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 Channel 管理器。
 *
 * <p>以 appName:key 为粒度维护推送通道。client 首次上报某个 key 时自动注册，
 * 热 key 推送仅投递到真正访问过该 key 的 Channel 集合。</p>
 */
public class ServerChannelManager {

    /**
     * key 级通道：appName:key → Channel 集合。
     */
    private final Map<String, Set<Channel>> keyChannels = new ConcurrentHashMap<>();

    /**
     * Channel 关闭时清理所有相关条目。
     */
    public void unregister(Channel channel) {
        for (Set<Channel> set : keyChannels.values()) {
            set.remove(channel);
        }
    }

    /**
     * 注册 key 级推送通道。
     */
    public void registerKeyChannel(String appName, String key, Channel channel) {
        if (appName == null || key == null || channel == null) {
            return;
        }
        String compositeKey = appName + ":" + key;
        keyChannels
                .computeIfAbsent(compositeKey, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(channel);
    }

    /**
     * 推送命令到真正访问过该 key 的 Channel 集合。
     */
    public void broadcastToKey(String appName, String key, Command command) {
        if (appName == null || key == null || command == null) {
            return;
        }
        String compositeKey = appName + ":" + key;
        Set<Channel> set = keyChannels.get(compositeKey);
        if (set == null) {
            return;
        }
        for (Channel channel : set) {
            if (channel.isActive()) {
                channel.writeAndFlush(command);
            }
        }
    }
}
