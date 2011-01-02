package net.sourceforge.guacamole.net.tunnel;

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

import net.sourceforge.guacamole.GuacamoleException;

import java.io.Reader;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.sourceforge.guacamole.net.GuacamoleSession;


public class Inbound extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException {

        HttpSession httpSession = request.getSession(false);

        try {

            GuacamoleSession session = new GuacamoleSession(httpSession);

            // Send data
            try {

                Reader input = request.getReader();
                char[] buffer = new char[8192];

                int length;
                while ((length = input.read(buffer, 0, buffer.length)) != -1)
                    session.getClient().write(buffer, 0, length);

            }
            catch (IOException e) {
                throw new GuacamoleException("I/O Error sending data to server: " + e.getMessage(), e);
            }

        }
        catch (GuacamoleException e) {
            throw new ServletException(e);
        }

    }

}

