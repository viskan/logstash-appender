package org.apache.logging.log4j.core.appender;

import static java.lang.System.lineSeparator;
import static org.apache.logging.log4j.Level.INFO;
import static org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link LogstashAppender}.
 */
public class LogstashAppenderTest extends Assert
{
    private static final String SEP = LogstashAppender.escape(lineSeparator());

    @Test
    public void test_appender_with_details() throws IOException
    {
        Logger logger = configure(true, false);

        byte[] received = new byte[1024];
        DatagramPacket packet = new DatagramPacket(received, received.length);

        try (DatagramSocket socket = new DatagramSocket(12345))
        {
            new Thread(new LoggerTask(logger, true)).start();

            socket.setSoTimeout(50000);
            socket.receive(packet);

            String actual = new String(received, 0, packet.getLength());
            String expected = getExpectedWithDetails();

            assertEquals(expected, actual);
        }
    }

    @Test
    public void test_appender_without_details() throws IOException
    {
        Logger logger = configure(false, false);

        byte[] received = new byte[1024];
        DatagramPacket packet = new DatagramPacket(received, received.length);

        try (DatagramSocket socket = new DatagramSocket(12345))
        {
            new Thread(new LoggerTask(logger, false)).start();

            socket.setSoTimeout(50000);
            socket.receive(packet);

            String actual = new String(received, 0, packet.getLength());
            String expected = getExpectedWithoutDetails();

            assertEquals(expected, actual);
        }
    }

    @Test
    public void test_empty_static_parameters() throws Exception
    {
        Logger logger = configure(false, false);

        byte[] received = new byte[1024];
        DatagramPacket packet = new DatagramPacket(received, received.length);

        try (DatagramSocket socket = new DatagramSocket(12345))
        {
            new Thread(() ->
            {
                logger.info("Test empty static keys!");
            }).start();

            socket.setSoTimeout(50000);
            socket.receive(packet);

            String actual = new String(received, 0, packet.getLength());
            String expected = "{\"message\":\"Test empty static keys!\",\"name\":\"org.apache.logging.log4j.core.appender.LogstashAppenderTest\",\"severity\":400,\"severityText\":\"INFO\"}";

            assertEquals("Expect the correct logger message", expected, actual);
        }
    }

    @Test
    public void test_static_parameters() throws Exception
    {
        Logger logger = configure(false, true);

        byte[] received = new byte[1024];
        DatagramPacket packet = new DatagramPacket(received, received.length);

        try (DatagramSocket socket = new DatagramSocket(12345))
        {
            new Thread(() ->
            {
                logger.info("Test static keys!");
            }).start();

            socket.setSoTimeout(50000);
            socket.receive(packet);

            String actual = new String(received, 0, packet.getLength());
            String expected = "{\"message\":\"Test static keys!\",\"name\":\"org.apache.logging.log4j.core.appender.LogstashAppenderTest\",\"severity\":400,\"severityText\":\"INFO\",\"group2\":\"REQUEST\",\"group\":\"PSP\"}";

            assertEquals("Expect the correct logger message", expected, actual);
        }
    }

    private Logger configure(boolean details, boolean hasParameters)
    {
        String application = null;
        String environment = null;
        boolean appendClassInformation = false;
        String mdcKeys = "";
        String parameters = "";

        if (details)
        {
            application = "Test";
            environment = "Development";
            appendClassInformation = true;
            mdcKeys = "key1,key2,key3";
        }

        if (hasParameters)
        {
            parameters = "group=PSP&group2=REQUEST";
        }

        LogstashAppender appender = new LogstashAppender(
                "TEST_APPENDER",
                null,
                createDefaultLayout(),
                application,
                environment,
                "localhost",
                12345,
                mdcKeys,
                parameters,
                appendClassInformation,
                Integer.MIN_VALUE);
        appender.start();

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.reconfigure();
        Configuration configuration = context.getConfiguration();
        configuration.getAppenders().remove(appender.getName());
        configuration.addAppender(appender);
        configuration.getLoggerConfig(LogstashAppenderTest.class.getName()).setLevel(INFO);
        context.updateLoggers();

        System.out.println(configuration.getAppenders());

        org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LogstashAppenderTest.class);
        logger.addAppender(appender);
        return logger;
    }

    private String getExpectedWithDetails()
    {
        return "{\"message\":\"Some message\",\"name\":\"org.apache.logging.log4j.core.appender.LogstashAppenderTest\",\"severity\":200,\"severityText\":\"ERROR\",\"application\":\"Test\",\"environment\":\"Development\",\"className\":\"org.apache.logging.log4j.core.appender.LogstashAppenderTest$LoggerTask\",\"lineNumber\":211,\"stacktrace\":\"java.lang.Exception: Error"
            + SEP + "\\tat org.apache.logging.log4j.core.appender.LogstashAppenderTest$LoggerTask.run(LogstashAppenderTest.java:211)" + SEP + "\\tat java.lang.Thread.run(Thread.java:748)" + SEP
            + "\",\"key2\":\"value2\"}";
    }

    private String getExpectedWithoutDetails()
    {
        return "{\"message\":\"Hello World!\",\"name\":\"org.apache.logging.log4j.core.appender.LogstashAppenderTest\",\"severity\":400,\"severityText\":\"INFO\"}";
    }

    /**
     * Task for executing the logging.
     */
    private static class LoggerTask implements Runnable
    {
        private final Logger logger;
        private final boolean details;

        private LoggerTask(Logger logger, boolean details)
        {
            this.logger = logger;
            this.details = details;
        }

        @Override
        public void run()
        {
            try
            {
                Thread.sleep(200);
            }
            catch (InterruptedException e)
            {
            }

            if (details)
            {
                ThreadContext.put("key2", "value2");
                ThreadContext.put("key4", "value4");
                logger.error("Some message", new Exception("Error"));
                ThreadContext.remove("key2");
                ThreadContext.remove("key4");
            }
            else
            {
                logger.info("Hello World!");
            }
        }
    }
}
