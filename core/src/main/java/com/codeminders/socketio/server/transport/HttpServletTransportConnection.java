package com.codeminders.socketio.server.transport;

import com.codeminders.socketio.common.ConnectionState;
import com.codeminders.socketio.common.SocketIOException;
import com.codeminders.socketio.protocol.BinaryPacket;
import com.codeminders.socketio.protocol.EngineIOPacket;
import com.codeminders.socketio.protocol.EngineIOProtocol;
import com.codeminders.socketio.protocol.SocketIOPacket;
import com.codeminders.socketio.server.Session;
import com.codeminders.socketio.server.SocketIOProtocolException;
import com.codeminders.socketio.server.Transport;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Alexander Sova (bird@codeminders.com)
 */
public class HttpServletTransportConnection extends AbstractTransportConnection
{
    private static final Logger LOGGER = Logger.getLogger(HttpServletTransportConnection.class.getName());

//    private HttpServletRequest request;
//    private HttpServletResponse response;

    private BlockingQueue<EngineIOPacket> packets = new LinkedBlockingDeque<>();

    private boolean done = false;

    public HttpServletTransportConnection(Transport transport)
    {
        super(transport);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
//        this.request = request;
//        this.response = response;

        if ("POST".equals(request.getMethod())) //incoming
        {
            response.setContentType("text/plain");

            String contentType = request.getContentType();
            if (contentType.startsWith("text/"))
            {
                String payload = CharStreams.toString(request.getReader());

                for (EngineIOPacket packet : EngineIOProtocol.decodePayload(payload))
                    getSession().onPacket(packet);
            }
            else
            {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                throw new SocketIOProtocolException("Unsupported request content type for incoming polling request: " + contentType);
            }
            response.getWriter().print("ok");
        }
        else if ("GET".equals(request.getMethod())) //outgoing
        {
            response.setContentType("application/octet-stream");
            try
            {

                OutputStream os = response.getOutputStream();
                for (EngineIOPacket packet = packets.take(); packet != null; packet = packets.poll())
                    EngineIOProtocol.binaryEncode(packet, os);

                response.flushBuffer();
            }
            catch (InterruptedException e)
            {
                if (LOGGER.isLoggable(Level.FINE))
                    LOGGER.log(Level.FINE, "Polling connection interrupted", e);
            }
        }
        else
        {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Override
    public void abort()
    {
        //TODO: interrupt lock/wait on new packet to send. use 'poison pill shutdown' method
        //TODO: release currently opened HTTP Connection

        //TODO: do we need to call onShutdown()?
        //TODO: what if we are closing connection but not whole session? Do we need a special call for this?
//        getSession().onShutdown();
    }

    @Override
    public void send(EngineIOPacket packet) throws SocketIOException
    {
        packets.add(packet);
    }

    @Override
    public void send(SocketIOPacket packet) throws SocketIOException
    {
        //TODO: binary (payload) encoding here. that should include binary objects too
        send(EngineIOProtocol.createMessagePacket(packet.encode()));
    }
}
