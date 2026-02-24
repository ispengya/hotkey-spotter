package com.ispengya.hotkey.remoting.server;

import com.ispengya.hotkey.remoting.protocol.Command;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * ServerHandler 是 remoting 服务端的入站处理器。
 *
 * <p>负责在连接建立/断开时更新 {@link ServerChannelManager}，在收到
 * {@link Command} 时将请求交给 {@link ServerRequestDispatcher} 处理。</p>
 */
public final class ServerHandler extends SimpleChannelInboundHandler<Command> {

    private final ServerChannelManager channelManager;
    private final ServerRequestDispatcher dispatcher;

    /**
     * 构造 ServerHandler。
     *
     * @param channelManager 连接管理器
     * @param dispatcher     请求分发器
     */
    public ServerHandler(ServerChannelManager channelManager,
                         ServerRequestDispatcher dispatcher) {
        this.channelManager = channelManager;
        this.dispatcher = dispatcher;
    }

    /**
     * 当有新连接建立时回调。
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        channelManager.register(ctx.channel());
    }

    /**
     * 当连接断开时回调。
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        channelManager.unregister(ctx.channel());
    }

    /**
     * 收到一条 Command 消息时回调。
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Command msg) {
        dispatcher.dispatch(ctx, msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                ctx.close();
                return;
            }
        }
        super.userEventTriggered(ctx, evt);
    }
}
