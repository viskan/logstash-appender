package com.viskan.logstash.appender;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

/**
 * Log4j appender that appends logs to Logstash in a JSON-format that is very
 * easy for Logstash to consume and dump directly into Elastic Search, for example.
 *
 * @author Anton Johansson
 */
public class LogstashAppender extends AppenderSkeleton
{
	private String application = "";
	private String logstashHost = "";
	private int logstashPort;
	private String mdcKeys = "";
	private boolean appendClassInformation;

	private DatagramSocket socket;
	private InetAddress address;
	private String[] actualMdcKeys;

	public String getApplication()
	{
		return application;
	}

	public void setApplication(String application)
	{
		this.application = application;
	}

	public String getLogstashHost()
	{
		return logstashHost;
	}

	public void setLogstashHost(String logstashHost)
	{
		this.logstashHost = logstashHost;
	}

	public int getLogstashPort()
	{
		return logstashPort;
	}

	public void setLogstashPort(int logstashPort)
	{
		this.logstashPort = logstashPort;
	}

	public String getMdcKeys()
	{
		return mdcKeys;
	}

	public void setMdcKeys(String mdcKeys)
	{
		this.mdcKeys = mdcKeys;
	}

	public boolean isAppendClassInformation()
	{
		return appendClassInformation;
	}

	public void setAppendClassInformation(boolean appendClassInformation)
	{
		this.appendClassInformation = appendClassInformation;
	}

	/**
	 * Appends given logging event to Logstash.
	 */
	@Override
	protected void append(LoggingEvent event)
	{
		if (socket == null)
		{
			return;
		}

		String data = getData(event);
		byte[] buffer = data.getBytes();

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

	private String getData(LoggingEvent event)
	{
		StringBuilder data = new StringBuilder();
		addValue(data, "message", event.getMessage());
		addValue(data, "loggerName", event.getLoggerName());
		addValue(data, "application", application);
		addValue(data, "severity", event.getLevel().getSyslogEquivalent());
		addValue(data, "severityText", event.getLevel());

		if (appendClassInformation)
		{
			LocationInfo locationInformation = event.getLocationInformation();
			addValue(data, "className", locationInformation.getClassName());
			addValue(data, "lineNumber", locationInformation.getLineNumber());
		}

		ThrowableInformation throwableInformation = event.getThrowableInformation();
		if (throwableInformation != null)
		{
			addValue(data, "stacktrace", getStacktrace(throwableInformation.getThrowable()));
		}

		for (String mdcKey : actualMdcKeys)
		{
			Object mdcValue = event.getMDC(mdcKey);
			if (mdcValue != null)
			{
				addValue(data, mdcKey, mdcValue);
			}
		}

		return data.append(" }").toString();
	}

	private void addValue(StringBuilder data, String key, Object value)
	{
		boolean isFirstValue = data.toString().isEmpty();

		if (isFirstValue)
		{
			data.append("{ ");
		}
		else
		{
			data.append(", ");
		}

		data.append("\"")
			.append(escape(key))
			.append("\": \"")
			.append(escape(value.toString()))
			.append("\"");
	}

	private Object getStacktrace(Throwable throwable)
	{
		try (StringWriter stringWriter = new StringWriter();
			 PrintWriter printWriter = new PrintWriter(stringWriter))
		{
			throwable.printStackTrace(printWriter);
			return stringWriter.toString();
		}
		catch (IOException e)
		{
			System.err.println("Error when fetching stacktrace");
			return null;
		}
	}

	/**
	 * We use UDP when appending to Logstash, so we do not need to close the connection.
	 *
	 */
	@Override
	public void close()
	{
		if (socket != null)
		{
			socket.close();
		}
	}

	/**
	 * This appender does not require a layout, hence we return {@code false}.
	 *
	 * @return Returns {@code false}.
	 */
	@Override
	public boolean requiresLayout()
	{
		return false;
	}

	/**
	 * Creates the UDP socket.
	 */
	@Override
	public void activateOptions()
	{
		actualMdcKeys = mdcKeys.replaceAll(" ", "").split(",");

		try
		{
			address = InetAddress.getByName(logstashHost);
			socket = new DatagramSocket();
		}
		catch (UnknownHostException e)
		{
			System.err.println("Could not find host: " + logstashHost);
		}
		catch (SocketException e)
		{
			System.err.println("Could not create UDP socket");
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
	private String escape(String s){
		if(s==null)
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
    private void escape(String s, StringBuffer sb) {
		for(int i=0;i<s.length();i++){
			char ch=s.charAt(i);
			switch(ch){
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
				if((ch>='\u0000' && ch<='\u001F') || (ch>='\u007F' && ch<='\u009F') || (ch>='\u2000' && ch<='\u20FF')){
					String ss=Integer.toHexString(ch);
					sb.append("\\u");
					for(int k=0;k<4-ss.length();k++){
						sb.append('0');
					}
					sb.append(ss.toUpperCase());
				}
				else{
					sb.append(ch);
				}
			}
		}//for
	}
    //CSON
}
