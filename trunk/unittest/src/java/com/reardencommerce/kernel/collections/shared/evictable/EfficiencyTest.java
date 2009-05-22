package com.reardencommerce.kernel.collections.shared.evictable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.reardencommerce.kernel.collections.shared.evictable.ConcurrentLinkedHashMap.EvictionPolicy;
import com.reardencommerce.kernel.collections.shared.evictable.caches.Cache;
import com.reardencommerce.kernel.collections.shared.evictable.distribution.Distribution;

/**
 * The efficiency tests for the {@link ConcurrentLinkedHashMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
public final class EfficiencyTest extends BaseTest {
    private Distribution distribution;
    private int size;

    @BeforeClass(groups="efficiency")
    public void beforeEfficiency() {
        size = Integer.valueOf(System.getProperty("efficiency.workingSetSize"));
        distribution = Distribution.valueOf(System.getProperty("efficiency.distribution").toUpperCase());
    }

    /**
     * Compares the hit rate of different cache implementations.
     */
    @Test(groups="efficiency")
    public void efficency() {
        List<Long> workingSet = createWorkingSet(distribution, size);
        debug("WorkingSet:\n%s", workingSet);
        for (Cache type : Cache.values()) {
            Map<Long, Long> cache = type.create(capacity, size, 1);
            double hits = determineEfficiency(cache, workingSet);
            double misses = size - hits;
            info("%s: hits=%s (%s percent), misses=%s (%s percent)", type,
                 NumberFormat.getInstance().format(hits),
                 NumberFormat.getPercentInstance().format(hits/size),
                 NumberFormat.getInstance().format(misses),
                 NumberFormat.getPercentInstance().format(misses/size));
        }
    }

    /**
     * Tests that entries are evicted in FIFO order using a complex working set.
     */
    @Test(groups="development")
    public void FifoEfficency() {
        debug(" * Fifo-efficency: START");
        ConcurrentLinkedHashMap<Long, Long> actual = create(EvictionPolicy.FIFO);
        Map<Long, Long> expected = Cache.SYNC_FIFO.create(capacity, capacity, 1);
        doEfficencyTest(actual, expected);
    }

    /**
     * Tests that entries are evicted in Second Chance FIFO order using a complex working set.
     */
    @Test(groups="development")
    public void SecondChanceEfficency() {
        debug(" * SecondChance-efficency: START");
        ConcurrentLinkedHashMap<Long, Long> actual = create(EvictionPolicy.SECOND_CHANCE);
        Map<Long, Long> expected = Cache.FAST_FIFO_2C.create(capacity, capacity, 1);
        doEfficencyTest(actual, expected);
    }

    /**
     * Tests that entries are evicted in LRU order using a complex working set.
     */
    @Test(groups="development")
    public void LruEfficency() {
        debug(" * Lru-efficency: START");
        ConcurrentLinkedHashMap<Long, Long> actual = create(EvictionPolicy.LRU);
        Map<Long, Long> expected = Cache.SYNC_LRU.create(capacity, capacity, 1);
        doEfficencyTest(actual, expected);
    }

    /**
     * Creates a random working set based on the distribution.
     *
     * @param distribution The distribution type to use.
     * @param size         The size of the working set.
     * @return             A random working set.
     */
    public List<Long> createWorkingSet(Distribution distribution, int size) {
        Callable<Double> algorithm = distribution.getAlgorithm();
        List<Long> workingSet = new ArrayList<Long>(size);
        for (int i=0; i<size; i++) {
            try {
                workingSet.add(Math.round(algorithm.call()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return workingSet;
    }

    /**
     * Determines the hit-rate of the cache.
     *
     * @param cache      The self-evicting map.
     * @param workingSet The request working set.
     * @return           The hit-rate.
     */
    public int determineEfficiency(Map<Long, Long> cache, List<Long> workingSet) {
        int hits = 0;
        for (Long key : workingSet) {
            if (cache.get(key) == null) {
                cache.put(key, 0L);
            } else {
                hits++;
            }
        }
        return hits;
    }

    /**
     * Executes a complex eviction test.
     */
    private void doEfficencyTest(ConcurrentLinkedHashMap<Long, Long> actual, Map<Long, Long> expected) {
        List<Long> workingSet = createWorkingSet(Distribution.EXPONENTIAL, 10*capacity);
        long hitExpected = determineEfficiency(expected, workingSet);
        long hitActual = determineEfficiency(actual, workingSet);
        assertTrue(actual.equals(expected), "Expected \n" + expected + "\nActual:\n" + expected);
        assertEquals(hitActual, hitExpected);
        assertTrue(hitExpected > 0);
        assertTrue(hitActual > 0);
        validator.state(actual);
    }
}
