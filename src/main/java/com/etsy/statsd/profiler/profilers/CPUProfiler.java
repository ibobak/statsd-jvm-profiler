package com.etsy.statsd.profiler.profilers;

import com.etsy.statsd.profiler.Arguments;
import com.etsy.statsd.profiler.Profiler;
import com.etsy.statsd.profiler.reporter.Reporter;
import com.etsy.statsd.profiler.util.*;
import com.etsy.statsd.profiler.worker.ProfilerThreadFactory;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ThreadInfo;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Profiles CPU time spent in each method
 *
 * @author Andrew Johnson
 */
public class CPUProfiler extends Profiler {
    private static Logger LOG = LoggerFactory.getLogger(CPUProfiler.class);

    private static final String PACKAGE_WHITELIST_ARG = "packageWhitelist";
    private static final String PACKAGE_BLACKLIST_ARG = "packageBlacklist";

    public static final long REPORTING_PERIOD = 10;
    public static final long PERIOD = 10; // Ihor Bobak:  it will be each 10 milliseconds, that is 100 times per second.
    public static final List<String> EXCLUDE_PACKAGES = Arrays.asList("com.etsy.statsd.profiler", "com.timgroup.statsd");

    private CPUTraces traces;
    private int profileCount;
    private StackTraceFilter filter;
    private long reportingFrequency;


    public CPUProfiler(Reporter reporter, Arguments arguments) {
        super(reporter, arguments);
        traces = new CPUTraces();
        profileCount = 0;
        reportingFrequency = TimeUtil.convertReportingPeriod(getPeriod(), getTimeUnit(), REPORTING_PERIOD, TimeUnit.SECONDS);
    }

    /**
     * Profile CPU time by method call
     */
    @Override
    public void profile() {
        profileCount++;
        try{
            for (ThreadInfo thread : getAllRunnableThreads()) {
                // certain threads do not have stack traces
                if (thread.getStackTrace().length > 0) {
                    String traceKey = StackTraceFormatter.formatStackTrace(thread.getStackTrace()); // this method adds "cpu.trace" to beginning of the line
                    if (filter.includeStackTrace(traceKey)) {
                        traces.increment(traceKey, 1);  // Ihor Bobak:  I understand what Andrew tried to do here by adding "PERIOD":
                        // he tried to increment it by 10 in the case if this is sampled each 10 milliseconds, like
                        // "we have been here 10 times".  But unfortunately, the use case shows that it can scan traces
                        // 30 times per seconds in a real cluster with good hardware. It means that even adding 10 will NOT give a clear picture.
                        // therefore, let it be the real figure here, and we'll have the ACTUAL number of samples, no matter how frequent they are done.
                    }
                }
            }
        }
        catch (OutOfMemoryError ex)
        {
            int size = traces.size();
            long sizeInChars = traces.sizeInChars();
            recordGaugeValue("cpu.OOM.size", size);
            recordGaugeValue("cpu.OOM.sizeInChars", sizeInChars);
            LOG.error(String.format("CPUProfiler OOM: size=%d, sizeInChars=%d ", size, sizeInChars), ex);
            recordMethodCounts(); // flush everything that we have in order to avoid the same in the future run of profile()
        }

        // To keep from overwhelming StatsD, we only report statistics every ten seconds
        if (profileCount == reportingFrequency) { 
            // Ihor Bobak: I don't want it to come over 2^31 :)
            profileCount = 0;
            recordMethodCounts();
        }
    }

    /**
     * Flush methodCounts data on shutdown
     */
    @Override
    public void flushData() {
        recordMethodCounts();
        // These bounds are recorded to help speed up generating flame graphs
        Range bounds = traces.getBounds();
        recordGaugeValue("cpu.trace." + bounds.getLeft(), bounds.getLeft());
        recordGaugeValue("cpu.trace." + bounds.getRight(), bounds.getRight());
        // finalize by sending the values of trace sizes
        recordGaugeValue("cpu.stats.size", traces.size());
        recordGaugeValue("cpu.stats.sizeInChars", traces.sizeInChars());
    }

    @Override
    public long getPeriod() {
        return PERIOD;
    }

    @Override
    public TimeUnit getTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    protected void handleArguments(Arguments arguments) {
        List<String> packageWhitelist = parsePackageList(arguments.remainingArgs.get(PACKAGE_WHITELIST_ARG));
        List<String> packageBlacklist = parsePackageList(arguments.remainingArgs.get(PACKAGE_BLACKLIST_ARG));
        filter = new StackTraceFilter(packageWhitelist, Lists.newArrayList(Iterables.concat(EXCLUDE_PACKAGES, packageBlacklist)));
    }

    /**
     * Parses a colon-delimited list of packages
     *
     * @param packages A string containing a colon-delimited list of packages
     * @return A List of packages
     */
    private List<String> parsePackageList(String packages) {
        if (packages == null) {
            return new ArrayList<>();
        } else {
            return Arrays.asList(packages.split(":"));
        }
    }

    /**
     * Records method CPU time in StatsD
     */
    private void recordMethodCounts() {
        // Ihor Bobak:
        // 1) we don't need to re-create another map with prefixed strings
        // because we already add "cpu.trace." in the formatStackTrace()
        // 2) we will send the size
        recordGaugeValue("cpu.stats.size", traces.size());
        recordGaugeValue("cpu.stats.sizeInChars", traces.sizeInChars());
        recordGaugeValues(traces.getDataToFlush());
    }

    /**
     * Gets all runnable threads, excluding profiler threads
     *
     * @return A Collection<ThreadInfo> representing current thread state
     */
    private Collection<ThreadInfo> getAllRunnableThreads() {
        return ThreadDumper.filterAllThreadsInState(false, false, Thread.State.RUNNABLE, new Predicate<ThreadInfo>() {
            @Override
            public boolean apply(ThreadInfo input) {
                return !input.getThreadName().startsWith(ProfilerThreadFactory.NAME_PREFIX);
            }
        });
    }
}