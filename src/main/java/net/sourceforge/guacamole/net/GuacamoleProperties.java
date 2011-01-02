
package net.sourceforge.guacamole.net;

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
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import net.sourceforge.guacamole.GuacamoleException;
import net.sourceforge.guacamole.net.authentication.GuacamoleClientProvider;

public class GuacamoleProperties {

    private static final Properties properties;
    private static GuacamoleException exception;

    static {

        properties = new Properties();

        try {

            InputStream stream = GuacamoleProperties.class.getResourceAsStream("/guacamole.properties");
            if (stream == null)
                throw new IOException("Resource /guacamole.properties not found.");

            properties.load(stream);
        }
        catch (IOException e) {
            exception = new GuacamoleException("Error reading guacamole.properties", e);
        }

    }

    public static String getProxyHostname() throws GuacamoleException {
        return GuacamoleProperties.getProperty("guacd-hostname");
    }

    public static int getProxyPort() throws GuacamoleException {
        return GuacamoleProperties.getIntProperty("guacd-port", null);
    }

    public static GuacamoleClientProvider getClientProvider() throws GuacamoleException {

        // Get client provider instance
        try {
            String sessionProviderClassName = GuacamoleProperties.getProperty("client-provider");
            Object obj = Class.forName(sessionProviderClassName).getConstructor().newInstance();
            if (!(obj instanceof GuacamoleClientProvider))
                throw new GuacamoleException("Specified client provider class is not a GuacamoleClientProvider");

            return (GuacamoleClientProvider) obj;
        }
        catch (ClassNotFoundException e) {
            throw new GuacamoleException("Session provider class not found", e);
        }
        catch (NoSuchMethodException e) {
            throw new GuacamoleException("Default constructor for client provider not present", e);
        }
        catch (SecurityException e) {
            throw new GuacamoleException("Creation of client provider disallowed; check your security settings", e);
        }
        catch (InstantiationException e) {
            throw new GuacamoleException("Unable to instantiate client provider", e);
        }
        catch (IllegalAccessException e) {
            throw new GuacamoleException("Unable to access default constructor of client provider", e);
        }
        catch (InvocationTargetException e) {
            throw new GuacamoleException("Internal error in constructor of client provider", e.getTargetException());
        }

    }

    public static String getProperty(String name) throws GuacamoleException {
        if (exception != null) throw exception;
        return properties.getProperty(name);
    }

    protected static String humanReadableList(Object... values) {

        String list = "";
        for (int i=0; i<values.length; i++) {

            if (i >= 1)
                list += ", ";

            if (i == values.length -1)
                list += " or ";

            list += "\"" + values[i] + "\"";
        }

        return list;

    }

    public static String getProperty(String name, String defaultValue, String... allowedValues) throws GuacamoleException {

        String value = getProperty(name);

        // Use default if not specified
        if (value == null) {
            if (defaultValue == null)
                throw new GuacamoleException("Parameter \"" + name + "\" is required.");

            return defaultValue;
        }

        // If not restricted to certain values, just return whatever is given.
        if (allowedValues.length == 0)
            return value;

        // If restricted, only return value within given list
        for (String allowedValue : allowedValues)
            if (value.equals(allowedValue))
                return value;

        throw new GuacamoleException("Parameter \"" + name + "\" must be " + humanReadableList((Object) allowedValues));
    }

    public static boolean getBooleanProperty(String name, Boolean defaultValue) throws GuacamoleException {

        String value = getProperty(name);

        // Use default if not specified
        if (value == null) {
            if (defaultValue == null)
                throw new GuacamoleException("Parameter \"" + name + "\" is required.");

            return defaultValue;
        }

        value = value.trim();
        if (value.equals("true"))
            return true;

        if (value.equals("false"))
            return false;

        throw new GuacamoleException("Parameter \"" + name + "\" must be \"true\" or \"false\".");

    }

    public static int getIntProperty(String name, Integer defaultValue, Integer... allowedValues) throws GuacamoleException {

        String parmString = getProperty(name);

        // Use default if not specified
        if (parmString== null) {
            if (defaultValue == null)
                throw new GuacamoleException("Parameter \"" + name + "\" is required.");

            return defaultValue;
        }

        try {
            int value = Integer.parseInt(parmString);

            // If not restricted to certain values, just return whatever is given.
            if (allowedValues.length == 0)
                return value;

            // If restricted, only return value within given list
            for (int allowedValue : allowedValues)
                if (value == allowedValue)
                    return value;

            throw new GuacamoleException("Parameter \"" + name + "\" must be " + humanReadableList((Object) allowedValues));
        }
        catch (NumberFormatException e) {
            throw new GuacamoleException("Parameter \"" + name + "\" must be an integer.", e);
        }

    }

}
