package ru.daniilazarnov;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.log4j.Logger;

/**
 * Класс содержит логику обработки принятых сообщений на стороне клиента
 */
public class ClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = Logger.getLogger(ClientHandler.class);
    private static final String user ="user1";
    private State currentState = State.IDLE;

    public ClientHandler() {
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        ByteBuf buf = ((ByteBuf) msg);

        if (currentState == State.IDLE) {
            byte readed = buf.readByte();

            switch (readed) {
                case (byte) 2:
                    ReceivingFiles.fileReceive(msg, user);
                    Client.printPrompt(); // вывод строки приглашения к вводу
                    break;

                case (byte) 4:
                    String catalogStrings = ReceivingAndSendingStrings.receiveAndEncodeString(buf);
                    System.out.println(catalogStrings);
                    break;

                default:
//                    invalidControlByte(buf, "(class FileReceiveHandler) ERROR: Invalid first byte - ", readed);

            }
        }





    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
        ctx.close();
        cause.printStackTrace();
    }
}
