package com.viskan.logstash.appender;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link LogstashAppender}.
 *
 * @author Anton Johansson
 */
public class LogstashAppenderTest extends Assert
{
	private static final Logger LOGGER = Logger.getLogger(LogstashAppenderTest.class);

	@Test
	public void test_appender_with_details() throws IOException
	{
		configure(true);
		
		byte[] received = new byte[1024];
		DatagramPacket packet = new DatagramPacket(received, received.length);
		
		try (DatagramSocket socket = new DatagramSocket(12345))
		{
			new Thread(new LoggerTask(true)).start();
			
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
		configure(false);
		
		byte[] received = new byte[1024];
		DatagramPacket packet = new DatagramPacket(received, received.length);
		
		try (DatagramSocket socket = new DatagramSocket(12345))
		{
			new Thread(new LoggerTask(false)).start();
			
			socket.setSoTimeout(50000);
			socket.receive(packet);
			
			String actual = new String(received, 0, packet.getLength());
			String expected = getExpectedWithoutDetails();
			
			assertEquals(expected, actual);
		}
	}

	private void configure(boolean details)
	{
		LogstashAppender appender = new LogstashAppender();
		appender.setLogstashHost("localhost");
		appender.setLogstashPort(12345);
		
		if (details)
		{
			appender.setApplication("Test");
			appender.setEnvironment("Development");
			appender.setAppendClassInformation(true);
			appender.setMdcKeys("key1,key2,key3");
		}
		
		appender.activateOptions();
		
		Logger.getRootLogger().removeAllAppenders();
		Logger.getRootLogger().addAppender(appender);
	}

	private String getExpectedWithDetails()
	{
		return "{\"message\":\"Some message\",\"name\":\"com.viskan.logstash.appender.LogstashAppenderTest\",\"severity\":3,\"severityText\":\"ERROR\",\"application\":\"Test\",\"environment\":\"Development\",\"className\":\"com.viskan.logstash.appender.LogstashAppenderTest$LoggerTask\",\"lineNumber\":\"124\",\"stacktrace\":\"java.lang.Exception: Error\\r\\n\\tat com.viskan.logstash.appender.LogstashAppenderTest$LoggerTask.run(LogstashAppenderTest.java:124)\\r\\n\\tat java.lang.Thread.run(Thread.java:744)\\r\\n\",\"key2\":\"value2\"}";
	}

	private String getExpectedWithoutDetails()
	{
		return "{\"message\":\"Hello World!\",\"name\":\"com.viskan.logstash.appender.LogstashAppenderTest\",\"severity\":6,\"severityText\":\"INFO\"}";
	}
	
	/**
	 * Task for executing the logging.
	 *
	 * @author Anton Johansson
	 */
	private static class LoggerTask implements Runnable
	{
		boolean details;
		
		private LoggerTask(boolean details)
		{
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
				MDC.put("key2", "value2");
				MDC.put("key4", "value4");
				LOGGER.error("Some message", new Exception("Error"));
				MDC.remove("key2");
				MDC.remove("key4");
			}
			else
			{
				LOGGER.info("Hello World!");
			}
		}
	}
}
