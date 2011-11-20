package net.sourceforge.guacamole.servlet;

/*
 *  Guacamole - Clientless Remote Desktop
 *  Copyright (C) 2010  Michael Jumper
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import net.sourceforge.guacamole.net.GuacamoleTunnel;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.sourceforge.guacamole.GuacamoleException;
import net.sourceforge.guacamole.io.GuacamoleReader;
import net.sourceforge.guacamole.io.GuacamoleWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A HttpServlet implementing and abstracting the operations required by the
 * JavaScript Guacamole client's tunnel.
 *
 * @author Michael Jumper
 */
public abstract class GuacamoleTunnelServlet extends HttpServlet {

    private Logger logger = LoggerFactory.getLogger(GuacamoleTunnelServlet.class);
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        handleTunnelRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        handleTunnelRequest(request, response);
    }

    /**
     * Dispatches every HTTP GET and POST request to the appropriate handler
     * function based on the query string.
     *
     * @param request The HttpServletRequest associated with the GET or POST
     *                request received.
     * @param response The HttpServletResponse associated with the GET or POST
     *                 request received.
     * @throws ServletException If an error occurs while servicing the request.
     */
    protected void handleTunnelRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException {

        try {

            String query = request.getQueryString();
            if (query == null)
                throw new GuacamoleException("No query string provided.");

            // If connect operation, call doConnect() and return tunnel UUID
            // in response.
            if (query.equals("connect")) {

                GuacamoleTunnel tunnel = doConnect(request);
                if (tunnel != null) {

                    logger.info("Connection from {} succeeded.", request.getRemoteAddr());
                
                    try {
                        response.getWriter().print(tunnel.getUUID().toString());
                    }
                    catch (IOException e) {
                        throw new GuacamoleException(e);
                    }
                    
                }
                else
                    logger.info("Connection from {} failed.", request.getRemoteAddr());

            }

            // If read operation, call doRead() with tunnel UUID
            else if(query.startsWith("read:"))
                doRead(request, response, query.substring(5));

            // If write operation, call doWrite() with tunnel UUID
            else if(query.startsWith("write:"))
                doWrite(request, response, query.substring(6));

            // Otherwise, invalid operation
            else
                throw new GuacamoleException("Invalid tunnel operation: " + query);
        }

        // Catch any thrown guacamole exception and attempt to pass within the
        // HTTP response.
        catch (GuacamoleException e) {

            try {

                // If response not committed, send error code along with
                // message.
                if (!response.isCommitted()) {
                    response.setHeader("X-Guacamole-Error-Message", e.getMessage());
                    response.sendError(
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            e.getMessage()
                    );
                }

                // If unable to send error code, rethrow as servlet exception
                else
                    throw new ServletException(e);

            }
            catch (IOException ioe) {

                // If unable to send error at all due to I/O problems,
                // rethrow as servlet exception
                throw new ServletException(ioe);

            }

        }

    }

    /**
     * Called whenever the JavaScript Guacamole client makes a connection
     * request. It it up to the implementor of this function to define what
     * conditions must be met for a tunnel to be configured and returned as a
     * result of this connection request (whether some sort of credentials must
     * be specified, for example).
     *
     * @param request The HttpServletRequest associated with the connection
     *                request received. Any parameters specified along with
     *                the connection request can be read from this object.
     * @return A newly constructed GuacamoleTunnel if successful,
     *         null otherwise.
     * @throws GuacamoleException If an error occurs while constructing the
     *                            GuacamoleTunnel, or if the conditions
     *                            required for connection are not met.
     */
    protected abstract GuacamoleTunnel doConnect(HttpServletRequest request) throws GuacamoleException;

    /**
     * Called whenever the JavaScript Guacamole client makes a read request.
     * This function should in general not be overridden, as it already
     * contains a proper implementation of the read operation.
     *
     * @param request The HttpServletRequest associated with the read request
     *                received.
     * @param response The HttpServletResponse associated with the write request
     *                 received. Any data to be sent to the client in response
     *                 to the write request should be written to the response
     *                 body of this HttpServletResponse.
     * @param tunnelUUID The UUID of the tunnel to read from, as specified in
     *                   the write request. This tunnel must be attached to
     *                   the Guacamole session.
     * @throws GuacamoleException If an error occurs while handling the read
     *                            request.
     */
    protected void doRead(HttpServletRequest request, HttpServletResponse response, String tunnelUUID) throws GuacamoleException {

        HttpSession httpSession = request.getSession(false);
        GuacamoleSession session = new GuacamoleSession(httpSession);

        GuacamoleTunnel tunnel = session.getTunnel(tunnelUUID);
        if (tunnel == null)
            throw new GuacamoleException("No such tunnel.");

        // Obtain exclusive read access
        GuacamoleReader reader = tunnel.acquireReader();

        try {

            // Note that although we are sending text, Webkit browsers will
            // buffer 1024 bytes before starting a normal stream if we use
            // anything but application/octet-stream.
            response.setContentType("application/octet-stream");

            Writer out = response.getWriter();

            // Detach tunnel and throw error if EOF (and we haven't sent any
            // data yet.
            char[] message = reader.read();
            if (message == null)
                throw new GuacamoleException("Disconnected.");

            // For all messages, until another stream is ready (we send at least one message)
            do {

                // Get message output bytes
                out.write(message, 0, message.length);
                out.flush();
                response.flushBuffer();

                // No more messages another stream can take over
                if (tunnel.hasQueuedReaderThreads())
                    break;

            } while ((message = reader.read()) != null);

            // Close tunnel immediately upon EOF
            if (message == null)
                tunnel.close();
            
            // End-of-instructions marker
            out.write("0.;");
            out.flush();
            response.flushBuffer();

        }
        catch (GuacamoleException e) {

            // Detach and close
            session.detachTunnel(tunnel);
            tunnel.close();

            throw e;
        }
        catch (UnsupportedEncodingException e) {

            // Detach and close
            session.detachTunnel(tunnel);
            tunnel.close();

            throw new GuacamoleException("UTF-8 not supported by Java.", e);
        }
        catch (IOException e) {

            // Detach and close
            session.detachTunnel(tunnel);
            tunnel.close();
                
            throw new GuacamoleException("I/O error writing to servlet output stream.", e);
        }
        finally {
            tunnel.releaseReader();
        }

    }

    /**
     * Called whenever the JavaScript Guacamole client makes a write request.
     * This function should in general not be overridden, as it already
     * contains a proper implementation of the write operation.
     *
     * @param request The HttpServletRequest associated with the write request
     *                received. Any data to be written will be specified within
     *                the body of this request.
     * @param response The HttpServletResponse associated with the write request
     *                 received.
     * @param tunnelUUID The UUID of the tunnel to write to, as specified in
     *                   the write request. This tunnel must be attached to
     *                   the Guacamole session.
     * @throws GuacamoleException If an error occurs while handling the write
     *                            request.
     */
    protected void doWrite(HttpServletRequest request, HttpServletResponse response, String tunnelUUID) throws GuacamoleException {

        HttpSession httpSession = request.getSession(false);
        GuacamoleSession session = new GuacamoleSession(httpSession);

        GuacamoleTunnel tunnel = session.getTunnel(tunnelUUID);
        if (tunnel == null)
            throw new GuacamoleException("No such tunnel.");

        // We still need to set the content type to avoid the default of
        // text/html, as such a content type would cause some browsers to
        // attempt to parse the result, even though the JavaScript client
        // does not explicitly request such parsing.
        response.setContentType("application/octet-stream");
        response.setContentLength(0);

        // Send data
        try {

            GuacamoleWriter writer = tunnel.acquireWriter();

            Reader input = request.getReader();
            char[] buffer = new char[8192];

            int length;
            while ((length = input.read(buffer, 0, buffer.length)) != -1)
                writer.write(buffer, 0, length);

        }
        catch (IOException e) {

            // Detach and close
            session.detachTunnel(tunnel);
            tunnel.close();

            throw new GuacamoleException("I/O Error sending data to server: " + e.getMessage(), e);
        }
        finally {
            tunnel.releaseWriter();
        }

    }

}

