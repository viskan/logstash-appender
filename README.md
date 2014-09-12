logstash-appender
=================

A simple log4j appender that sends a JSON-object to a Logstash server.


Usage: logstash configuration
-----------------------------
A simple example of the logstash server configuration:

```json
input {
	udp {
		port => 8400
		codec => "json"
	}
}
output {
	elasticsearch {
		protocol => http
		host => "localhost"
		port => 9200
	}
}
```
	

Usage: log4j configuration
--------------------------
As a plain properties file:
```ini
# Logstash appender
log4j.appender.Logstash=com.viskan.logstash.appender.LogstashAppender
log4j.appender.Logstash.Threshold=INFO
log4j.appender.Logstash.application=SomeApplication
log4j.appender.Logstash.logstashHost=hostname.com
log4j.appender.Logstash.logstashPort=12345
log4j.appender.Logstash.mdcKeys=httpMethod, httpPath, responseTime, responseCode
log4j.appender.Logstash.appendClassInformation=false
```


Add appender to OSGi environment (Karaf)
----------------------------------------

First of all, you need to add the JAR as a system bundle. Add logstash-appender-1.0.0.jar to /system/com/viskan/logstash-appender/1.0.0/logstash-appender-1.0.0.jar.
Then you need to add it to the startup flow. Edit /etc/startup.properties so it looks something like this:

```ini
#
# Startup core services like logging
#
org/ops4j/pax/url/pax-url-mvn/1.3.5/pax-url-mvn-1.3.5.jar=5
org/ops4j/pax/url/pax-url-wrap/1.3.5/pax-url-wrap-1.3.5.jar=5
org/ops4j/pax/logging/pax-logging-api/1.7.0/pax-logging-api-1.7.0.jar=8
org/ops4j/pax/logging/pax-logging-service/1.7.0/pax-logging-service-1.7.0.jar=8
com/viskan/logstash-appender/1.0.0/logstash-appender-1.0.0.jar=8
org/apache/felix/org.apache.felix.configadmin/1.6.0/org.apache.felix.configadmin-1.6.0.jar=10
org/apache/felix/org.apache.felix.fileinstall/3.2.6/org.apache.felix.fileinstall-3.2.6.jar=11
```
