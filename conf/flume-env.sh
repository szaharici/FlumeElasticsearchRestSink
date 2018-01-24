JAVA_OPTS="-Dflume.monitoring.type=http -Dflume.monitoring.port=34545 -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=5445 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
FLUME_CLASSPATH=""
HIVE_HOME="/dev/null" # Otherwise flume-ng try to add Hive related classpath with /lib
FLUME_HOME="/opt/flume"
