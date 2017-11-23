package org.whitesource.agent.utils;

import org.junit.Assert;
import org.junit.Test;

public class MemoryUsageTest {

    @Test
    public void shouldReturnMemoryUsage() {
        MemoryUsageHelper.SystemStats result = MemoryUsageHelper.getMemoryUsage();
        Assert.assertFalse(result.freeMemory != 0);
        Assert.assertFalse(result.totalMemory != 0);
        Assert.assertFalse(result.usedMemory != 0);
        Assert.assertFalse(result.availableProcessors != 0);
    }
}