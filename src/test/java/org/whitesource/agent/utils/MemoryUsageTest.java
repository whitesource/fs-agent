package org.whitesource.agent.utils;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class MemoryUsageTest {

    @Ignore
    @Test
    public void shouldReturnMemoryUsage() {
        MemoryUsageHelper.SystemStats result = MemoryUsageHelper.getMemoryUsage();
        Assert.assertTrue(result.freeMemory != 0);
        Assert.assertTrue(result.totalMemory != 0);
        Assert.assertTrue(result.usedMemory != 0);
        Assert.assertTrue(result.availableProcessors != 0);
    }
}