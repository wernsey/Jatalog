package za.co.wstoop.jdatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/* Profiling class.
I had to write my own because I didn't want to pull in any external dependencies.
`buckets` maps the name of a bucket to a List of elapsed times so that you can have
multiple timers under different names.
*/
class Profiler {

    // FYI: Java 8 actually has Instant and Duration classes.
    // Not sure whether they're really useful here, though.

    static class Timer {
        private long start;
        private List<Long> bucket;

        Timer(List<Long> bucket) {
            start = System.currentTimeMillis();
            this.bucket = bucket;
        }

        long stop() {
            long elapsed = (System.currentTimeMillis() - start);
            bucket.add(elapsed);
            return elapsed;
        }
    }

    private Map<String, List<Long>> buckets;

    private static Profiler instance;

    private Profiler() {
        buckets = new ConcurrentHashMap<String, List<Long>>();
    }

    public static Profiler getInstance() {
        if(instance == null) {
            instance = new Profiler();
        }
        return instance;
    }

    public static void reset() {
        getInstance().buckets.clear();
    }

    public static Profiler.Timer getTimer(String name) {
        List<Long> bucket = getInstance().getBucket(name);
        return new Timer(bucket);
    }

    List<Long> getBucket(String name) {
        if(!buckets.containsKey(name)) {
            List<Long> list = Collections.synchronizedList(new ArrayList<Long>(JDatalog.NUM_RUNS));
            buckets.putIfAbsent(name, list);
        }
        return buckets.get(name);
    }

    public static double average(String name) {
        List<Long> times = getInstance().getBucket(name);
        if(times.size() == 0) {
            return 0;
        }
        synchronized(times) {
            long sum = 0;
            for(Long time : times) {
                sum += time;
            }
            return (double)sum / times.size();
        }
    }

    public static long total(String name) {
        List<Long> times = getInstance().getBucket(name);
        long sum = 0;
        synchronized(times) {
            for(Long time : times) {
                sum += time;
            }
        }
        return sum;
    }

    public static int count(String name) {
        return getInstance().getBucket(name).size();
    }

    public static double stdDev(String name) {
        // I'm sure I'm going to be really embarrased when I figure out
        // why the stdDev looks so strange...
        List<Long> times = getInstance().getBucket(name);
        synchronized(times) {
            double avg = average(name);
            double sumSq = 0.0;
            for(Long time : times) {
                sumSq += (time - avg) * (time - avg);
            }
            double variance = sumSq / times.size();
            return Math.sqrt(variance);
        }
    }

    public static Set<String> keySet() {
        return getInstance().buckets.keySet();
    }
}