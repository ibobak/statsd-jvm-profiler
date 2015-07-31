package com.etsy.statsd.profiler.util;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of the CPU profiler
 *
 * @author Andrew Johnson
 */
public class CPUTraces {
    private Map<String, Long> traces;
    private int max = Integer.MIN_VALUE;
    private int min = Integer.MAX_VALUE;

    public CPUTraces() {
        traces = new HashMap<>();
    }

    /**
     * Increment the aggregate time for a trace
     *
     * @param traceKey The key for the trace
     * @param inc The value by which to increment the aggregate time for the trace
     */
    public void increment(String traceKey, long inc) {
        // Ihor Bobak: we shouldn't pass here anything that doesn't begin with "cpu.trace"
        Preconditions.checkArgument(traceKey.startsWith("cpu.trace."));
        MapUtil.setOrIncrementMap(traces, traceKey, inc);
        updateBounds(traceKey);
    }

    /**
     * Get data to be flushed from the state
     * It only returns traces that have been updated since the last flush
     *
     */
    public Map<String, Long> getDataToFlush() {
        Map<String, Long> result = traces;
        traces = new HashMap<>();
        return result;
    }

    /**
     * Get the bounds on the number of path components for the CPU trace metrics
     *
     * @return A Pair of integers, the left being the minimum number of components and the right being the maximum
     */
    public Range getBounds() {
        return new Range(min, max);
    }

    /**
     * Returns the size of the traces
     *
     * @return
     */
    public int size() {
        return traces.size();
    }

    /**
     * Returns the number of characters in all the keys of the internal hashmap
     *
     * @return
     */
    public long sizeInChars() {
        long totalLen = 0;
        for (String key: traces.keySet())
            totalLen += key.length();
        return totalLen;
    }

    private void updateBounds(String traceKey) {
        int numComponents = 1; // Ihor Bobak: the number of components equals the number of dots + 1
        int len = traceKey.length();
        for (int i = 0; i < len; ++i)
            if (traceKey.charAt(i) == '.')
                numComponents++;
        max = Math.max(max, numComponents - 2);  // Ihor Bobak:  remember that traceKey contains "cpu.trace" prefix
        min = Math.min(min, numComponents - 2);
    }
}
