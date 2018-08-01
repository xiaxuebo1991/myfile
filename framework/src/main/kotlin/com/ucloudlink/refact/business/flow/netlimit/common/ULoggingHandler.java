//package com.ucloudlink.refact.business.flow.netlimit.common;
//
//import io.netty.buffer.ByteBuf;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.ChannelPromise;
//import io.netty.handler.logging.LogLevel;
//import io.netty.handler.logging.LoggingHandler;
//
///**
// * Created by jianguo.he on 2017/12/28.
// */
//
//public class ULoggingHandler extends LoggingHandler {
//
//    public ULoggingHandler(LogLevel level) {
//       super(level);
//    }
//
//    @Override
//    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        if(msg instanceof ByteBuf && msg!=null){
//            ByteBuf inputBuf = (ByteBuf)msg;
//            if(inputBuf!=null && inputBuf.array()!=null){
//                NetPackageStatisticsCtrl.getInstance().appendDecodeReadableBytes(inputBuf.array().length);
//            }
//        }
//        ctx.fireChannelRead(msg);
//    }
//
//    @Override
//    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
//        if(msg instanceof ByteBuf && msg!=null){
//            ByteBuf out = (ByteBuf)msg;
//            if(out!=null){
//                NetPackageStatisticsCtrl.getInstance().appendEncodeOutLen(out.array().length);
//            }
//        }
//        ctx.write(msg, promise);
//    }
//}


package com.ucloudlink.refact.business.flow.netlimit.common;

import java.net.SocketAddress;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Created by jianguo.he on 2017/12/28.
 */

public class ULoggingHandler implements ChannelOutboundHandler, ChannelInboundHandler {


    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof ByteBuf && msg!=null){
            ByteBuf inputBuf = (ByteBuf)msg;
            if(inputBuf!=null && inputBuf.array()!=null){
                NetPackageStatisticsCtrl.getInstance().appendDecodeReadableBytes(inputBuf.array().length);
            }
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadComplete();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if(msg instanceof ByteBuf && msg!=null){
            ByteBuf out = (ByteBuf)msg;
            if(out!=null){
                NetPackageStatisticsCtrl.getInstance().appendEncodeOutLen(out.array().length);
            }
        }
        ctx.write(msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }
}

