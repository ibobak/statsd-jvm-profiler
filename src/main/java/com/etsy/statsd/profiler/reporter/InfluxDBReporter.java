package com.etsy.statsd.profiler.reporter;

import com.etsy.statsd.profiler.Arguments;
import com.etsy.statsd.profiler.util.TagUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reporter that sends data to InfluxDB
 *
 * @author Andrew Johnson
 */
public class InfluxDBReporter extends Reporter<InfluxDB> {
    private static Logger LOG = LoggerFactory.getLogger(InfluxDBReporter.class);

    public static final String VALUE_COLUMN = "value";
    public static final String USERNAME_ARG = "username";
    public static final String PASSWORD_ARG = "password";
    public static final String DATABASE_ARG = "database";
    public static final String TAG_MAPPING_ARG = "tagMapping";

    private String prefix;
    private String username;
    private String password;
    private String database;
    private String tagMapping;
    private Map<String, String> tags;

    public InfluxDBReporter(Arguments arguments) {
        super(arguments);
        this.prefix = arguments.metricsPrefix;
        // If we have a tag mapping it must match the number of components of the prefix
        Preconditions.checkArgument(tagMapping == null || tagMapping.split("\\.").length == prefix.split("\\.").length);
        tags = TagUtil.getTags(tagMapping, prefix);

        // Ihor Bobak:
        // This is the way to separate data by the JVM ID, which is usually in the format  ID@hostname
        final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        tags.put("jvmName", jvmName);
        int atIndex = jvmName.indexOf("@");
        if (atIndex > 0)
        {
            tags.put("pid", jvmName.substring(0, atIndex));
            tags.put("host", jvmName.substring(atIndex + 1));
        }
        else {
            tags.put("pid", jvmName);
            tags.put("host", "unknown");
        }

        // TODO: find the way how to recognize by the command line parameters WHAT IS IS THIS:  is this a container, or appmaster, or zookeeper, or... what?
        // tags.put("recognizedType", !!! THE TYPE OF PROCESS YOU RECOGNIZED !!!);
        // TODO: and also try to recognize the name of the job - if this is a hadoop job
        // tags.put("recognizedJobID",  job_1437758498951_0001);
        // tags.put("recognizedJobName",  THE_NAME_WHICH_YOU_GAVE_THE_JOB);
    }

    /**
     * Record a gauge value in InfluxDB
     *
     * @param key The key for the gauge
     * @param value The value of the gauge
     */
    @Override
    public void recordGaugeValue(String key, long value) {
        Map<String, Long> gauges = ImmutableMap.of(key, value);
        recordGaugeValues(gauges);
    }

    /**
     * Record multiple gauge values in InfluxDB
     *
     * @param gauges A map of gauge names to values
     */
    @Override
    public void recordGaugeValues(Map<String, Long> gauges) {
        // Ihor Bobak: a piece of optimization
        if (gauges.size() == 0)
            return;
        long time = System.currentTimeMillis();
        BatchPoints batchPoints = BatchPoints.database(database).build();
        try{
            for (Map.Entry<String, Long> gauge: gauges.entrySet()) {
                batchPoints.point(constructPoint(time, gauge.getKey(), gauge.getValue()));
            }
        }
        catch (OutOfMemoryError ex){
            LOG.error("InfluxDBReporter.recordGaugeValues OutOfMemory failure at construction of batch points", ex);
        }

        try {
            client.write(batchPoints);
        }
        catch (OutOfMemoryError ex){
            LOG.error("InfluxDBReporter.recordGaugeValues OutOfMemory failure at client.write", ex);
        }
    }

    /**
     *
     * @param server The server to which to report data
     * @param port The port on which the server is running
     * @param prefix The prefix for metrics
     * @return An InfluxDB client
     */
    @Override
    protected InfluxDB createClient(String server, int port, String prefix) {
        return InfluxDBFactory.connect(String.format("http://%s:%d", server, port), username, password);
    }

    /**
     * Handle remaining arguments
     *
     * @param arguments The arguments given to the profiler agent
     */
    @Override
    protected void handleArguments(Arguments arguments) {
        username = arguments.remainingArgs.get(USERNAME_ARG);
        password = arguments.remainingArgs.get(PASSWORD_ARG);
        database = arguments.remainingArgs.get(DATABASE_ARG);
        tagMapping = arguments.remainingArgs.get(TAG_MAPPING_ARG);

        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(password);
        Preconditions.checkNotNull(database);
    }

    private Point constructPoint(long time, String key, long value) {
        Point.Builder builder = Point.measurement(key)
                .time(time, TimeUnit.MILLISECONDS)
                .field(VALUE_COLUMN, value);
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            builder = builder.tag(entry.getKey(), entry.getValue());
        }

        return builder.build();
    }
}
