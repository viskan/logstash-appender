package com.viskan.logstash.appender;

import org.apache.log4j.Logger;

/**
 * Manually tests {@link LogstashAppender}.
 *
 * @author Anton Johansson
 */
public class LogstashAppenderTestManually
{
	private static final Logger LOGGER = Logger.getLogger(LogstashAppenderTestManually.class);

	public static void main(String[] args)
	{
		LOGGER.info("Test");
	}
}
