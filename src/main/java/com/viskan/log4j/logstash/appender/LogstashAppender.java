package com.viskan.log4j.logstash.appender;

import static java.lang.Math.min;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Log4j appender that appends logs to Logstash in a JSON-format that is very easy for Logstash to consume and dump directly into Elasticsearch, for
 * example.
 */
@Plugin(name = "Logstash", category = "Core", elementType = "appender", printObject = true)
public final class LogstashAppender extends AbstractAppender
{
    private static final String TRUNCATED_BY_LOGSTASH_APPENDER = "...[truncated by logstash appender]";
    private static final int TRUNCATE_MSG_LENGTH = TRUNCATED_BY_LOGSTASH_APPENDER.length();

    private final String application;
    private final String environment;
    private final int logstashPort;
    private final boolean appendClassInformation;
    private final int stacktraceLength;
    private final String[] mdcKeys;
    private final Map<String, String> parameters;
    private final InetAddress address;
    private final DatagramSocket socket;

    //CSOFF
    LogstashAppender(
            String name,
            Filter filter,
            Layout<? extends Serializable> layout,
            String application,
            String environment,
            String logstashHost,
            int logstashPort,
            String mdcKeys,
            String parameters,
            boolean appendClassInformation,
            int stacktraceLength)
    //CSON
    {
        super(name, filter, layout, true);
        this.application = application;
        this.environment = environment;
        this.logstashPort = logstashPort;
        this.appendClassInformation = appendClassInformation;
        this.stacktraceLength = stacktraceLength;
        this.mdcKeys = mdcKeys.replaceAll(" ", "").split(",");
        this.parameters = asList(parameters.split("&"))
                .stream()
                .map(s -> s.split("=", 2))
                .filter(p -> p != null && p.length == 2)
                .collect(toMap(a -> a[0], a -> a[1]));

        this.address = getAddress(logstashHost);
        this.socket = getSocket();
    }

    private DatagramSocket getSocket()
    {
        try
        {
            return new DatagramSocket();
        }
        catch (SocketException e)
        {
            LOGGER.error("Could not create UDP socket");
            return null;
        }
    }

    private InetAddress getAddress(String logstashHost)
    {
        try
        {
            return InetAddress.getByName(logstashHost);
        }
        catch (UnknownHostException e)
        {
            LOGGER.error("Could not find host: " + logstashHost);
            return null;
        }
    }

    /**
     * Creates a new appender.
     *
     * @param name The name of the appender.
     * @param layout The layout.
     * @param filter The (optional) filter to use.
     * @param application The application field.
     * @param environment The environment field.
     * @param logstashHost The host of the logstash installation.
     * @param logstashPortString The port of the logstash installation.
     * @param mdcKeys A comma-separated list of MDC keys to send.
     * @param parameters A ampersand-separated list of parameters to send.
     * @param appendClassInformationString Whether or not to append class information.
     * @param stacktraceLengthString The length of the stacktrace where it will be truncated.
     * @return Returns the created appender.
     */
    //CSOFF
    @PluginFactory
    public static LogstashAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginElement("Filter") Filter filter,
            @PluginAttribute("application") String application,
            @PluginAttribute("environment") String environment,
            @PluginAttribute("logstashHost") String logstashHost,
            @PluginAttribute("logstashPort") String logstashPortString,
            @PluginAttribute("mdcKeys") String mdcKeys,
            @PluginAttribute("parameters") String parameters,
            @PluginAttribute("appendClassInformation") String appendClassInformationString,
            @PluginAttribute("stacktraceLength") String stacktraceLengthString)
    {
        if (name == null)
        {
            LOGGER.error("No name provided for LogstashAppender");
            return null;
        }

        if (layout == null)
        {
            layout = PatternLayout.createDefaultLayout();
        }

        int logstashPort = 0;
        try
        {
            logstashPort = Integer.parseInt(logstashPortString);
        }
        catch (NumberFormatException e)
        {
            LOGGER.error("logstashPort must be an integer value");
            return null;
        }

        if (mdcKeys == null)
        {
            mdcKeys = "";
        }

        if (parameters == null)
        {
            parameters = "";
        }

        boolean appendClassInformation = Boolean.parseBoolean(appendClassInformationString);

        int stacktraceLength = Integer.MIN_VALUE;
        if (stacktraceLengthString != null)
        {
            try
            {
                stacktraceLength = Integer.parseInt(stacktraceLengthString);
            }
            catch (NumberFormatException e)
            {
                LOGGER.error("stacktraceLength must be an integer value");
                return null;
            }
        }

        return new LogstashAppender(
                name,
                filter,
                layout,
                application,
                environment,
                logstashHost,
                logstashPort,
                mdcKeys,
                parameters,
                appendClassInformation,
                stacktraceLength);
    }
    //CSON

    @Override
    public void append(LogEvent event)
    {
        if (socket == null)
        {
            return;
        }

        StringBuilder data = getData(event);
        byte[] buffer = getBytes(data);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, logstashPort);
        try
        {
            socket.send(packet);
        }
        catch (IOException e)
        {
            System.err.println("Could not send UDP packet");
        }
    }

    private byte[] getBytes(StringBuilder builder)
    {
        Charset charset = StandardCharsets.UTF_8;
        CharsetEncoder encoder = charset.newEncoder();

        // No allocation performed, just wraps the StringBuilder.
        CharBuffer buffer = CharBuffer.wrap(builder);

        ByteBuffer byteBuffer;
        try
        {
            byteBuffer = encoder.encode(buffer);
        }
        catch (CharacterCodingException e)
        {
            throw new RuntimeException("Error encoding buffer", e);
        }

        byte[] array;
        int arrayLen = byteBuffer.limit();
        if (arrayLen == byteBuffer.capacity())
        {
            array = byteBuffer.array();
        }
        else
        {
            // This will place two copies of the byte sequence in memory,
            // until byteBuffer gets garbage-collected (which should happen
            // pretty quickly once the reference to it is null'd).

            array = new byte[arrayLen];
            byteBuffer.get(array);
        }
        byteBuffer = null;
        return array;
    }

    private StringBuilder getData(LogEvent event)
    {
        StringBuilder data = new StringBuilder();
        addValue(data, "message", event.getMessage().getFormattedMessage());
        addValue(data, "name", event.getLoggerName());
        addValue(data, "severity", event.getLevel().intLevel());
        addValue(data, "severityText", event.getLevel());

        if (application != null)
        {
            addValue(data, "application", application);
        }

        if (environment != null)
        {
            addValue(data, "environment", environment);
        }

        if (appendClassInformation)
        {
            StackTraceElement source = event.getSource();
            addValue(data, "className", source.getClassName());
            addValue(data, "lineNumber", source.getLineNumber());
        }

        Throwable thrown = event.getThrown();
        if (thrown != null)
        {
            addValue(data, "stacktrace", getStacktrace(thrown));
        }

        for (String mdcKey : mdcKeys)
        {
            String mdcValue = event.getContextData().getValue(mdcKey);
            if (mdcValue != null)
            {
                addValue(data, mdcKey, mdcValue);
            }
        }

        parameters.forEach((k, v) ->
        {
            addValue(data, k, v);
        });

        data.append("}");

        return data;
    }

    private void addValue(StringBuilder data, String key, Object value)
    {
        boolean isFirstValue = data.toString().isEmpty();
        boolean isNumber = value instanceof Number;

        if (isFirstValue)
        {
            data.append("{");
        }
        else
        {
            data.append(",");
        }

        data.append("\"")
                .append(escape(key))
                .append("\":");

        if (!isNumber)
        {
            data.append("\"");
        }

        if (value != null)
        {
            data.append(escape(value.toString()));
        }

        if (!isNumber)
        {
            data.append("\"");
        }
    }

    private Object getStacktrace(Throwable throwable)
    {
        try (StringWriter stringWriter = new StringWriter(); PrintWriter printWriter = new PrintWriter(stringWriter))
        {
            throwable.printStackTrace(printWriter);
            String stackTrace = stringWriter.toString();
            if (stacktraceLength >= 0)
            {
                return stackTrace.substring(0, min(stackTrace.length(), stacktraceLength) - TRUNCATE_MSG_LENGTH) + TRUNCATED_BY_LOGSTASH_APPENDER;
            }
            return stackTrace;
        }
        catch (IOException e)
        {
            System.err.println("Error when fetching stacktrace");
            return null;
        }
    }

    @Override
    public void stop()
    {
        super.stop();
        if (socket != null)
        {
            socket.close();
        }
    }

    /**
     * Escape quotes, \, /, \r, \n, \b, \f, \t and other control characters (U+0000 through U+001F).
     * <p>
     * Taken from the JSONValue class of json-simple-1.1.jar
     *
     * @param s
     * @return
     */
    //CSOFF
    static String escape(String s)
    {
        if (s == null)
        {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        escape(s, sb);
        return sb.toString();
    }

    /**
     * Taken from JSONValue class of json-simple-1.1.jar
     *
     * @param s - Must not be null.
     * @param sb
     */
    private static void escape(String s, StringBuffer sb)
    {
        for (int i = 0; i < s.length(); i++)
        {
            char ch = s.charAt(i);
            switch (ch)
            {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '/':
                    sb.append("\\/");
                    break;
                default:
                    //Reference: http://www.unicode.org/versions/Unicode5.1.0/
                    if ((ch >= '\u0000' && ch <= '\u001F') || (ch >= '\u007F' && ch <= '\u009F') || (ch >= '\u2000' && ch <= '\u20FF'))
                    {
                        String ss = Integer.toHexString(ch);
                        sb.append("\\u");
                        for (int k = 0; k < 4 - ss.length(); k++)
                        {
                            sb.append('0');
                        }
                        sb.append(ss.toUpperCase());
                    }
                    else
                    {
                        sb.append(ch);
                    }
            }
        }//for
    }
    //CSON
}
