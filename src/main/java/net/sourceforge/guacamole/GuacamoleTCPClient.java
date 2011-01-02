
package net.sourceforge.guacamole;

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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import java.io.Reader;
import java.io.InputStreamReader;

import java.io.Writer;
import java.io.OutputStreamWriter;


public class GuacamoleTCPClient extends GuacamoleClient {

    private Socket sock;
    private Reader input;
    private Writer output;

    public GuacamoleTCPClient(String hostname, int port) throws GuacamoleException {

        try {
            sock = new Socket(InetAddress.getByName(hostname), port);
            input = new InputStreamReader(sock.getInputStream());
            output = new OutputStreamWriter(sock.getOutputStream());
        }
        catch (IOException e) {
            throw new GuacamoleException(e);
        }

    }

    public void write(char[] chunk, int off, int len) throws GuacamoleException {
        try {
            output.write(chunk, off, len);
            output.flush();
        }
        catch (IOException e) {
            throw new GuacamoleException(e);
        }
    }

    public void disconnect() throws GuacamoleException {
        try {
            sock.close();
        }
        catch (IOException e) {
            throw new GuacamoleException(e);
        }
    }

    private int usedLength = 0;
    private char[] buffer = new char[20000];

    public char[] read() throws GuacamoleException {

        try {

            // While we're blocking, or input is available
            for (;;) {

                // If past threshold, resize buffer before reading
                if (usedLength > buffer.length/2) {
                    char[] biggerBuffer = new char[buffer.length*2];
                    System.arraycopy(buffer, 0, biggerBuffer, 0, usedLength);
                    buffer = biggerBuffer;
                }

                // Attempt to fill buffer
                int numRead = input.read(buffer, usedLength, buffer.length - usedLength);
                if (numRead == -1)
                    return null;

                int prevLength = usedLength;
                usedLength += numRead;

                for (int i=usedLength-1; i>=prevLength; i--) {

                    char readChar = buffer[i];

                    // If end of instruction, return it.
                    if (readChar == ';') {

                        // Get instruction
                        char[] chunk = new char[i+1];
                        System.arraycopy(buffer, 0, chunk, 0, i+1);

                        // Reset buffer
                        usedLength -= i+1;
                        System.arraycopy(buffer, i+1, buffer, 0, usedLength);

                        // Return instruction string
                        return chunk;
                    }

                }

            } // End read loop

        }
        catch (IOException e) {
            throw new GuacamoleException(e);
        }

    }

}
