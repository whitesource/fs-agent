/**
 * Copyright (C) 2017 WhiteSource Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.agent.utils;

/**
 * @author eugen.horovitz
 * Class helper for tracking memory usage
 *
 */
public class MemoryUsageHelper {

    /**
     *
     * @return Gets memory usage
     */
    public static SystemStats getMemoryUsage() {

        long mbRatio = 1024*1024;
        Runtime runTime = Runtime.getRuntime();

        return new SystemStats(runTime.availableProcessors(),runTime.freeMemory()/mbRatio,
                runTime.maxMemory()/mbRatio,runTime.totalMemory()/mbRatio,
                (runTime.totalMemory()-runTime.freeMemory())/mbRatio);
    }

    public static class SystemStats {
        int availableProcessors;
        long freeMemory;
        long maxMemory;
        long totalMemory;
        long usedMemory;

        private SystemStats(int availableProcessors, long freeMemory, long maxMemory, long totalMemory, long usedMemory) {
            this.availableProcessors = availableProcessors;
            this.freeMemory = freeMemory;
            this.maxMemory = maxMemory;
            this.totalMemory = totalMemory;
            this.usedMemory = usedMemory;
        }

        public int getAvailableProcessors() {
            return availableProcessors;
        }

        public long getFreeMemory() {
            return freeMemory;
        }

        public long getMaxMemory() {
            return maxMemory;
        }

        public long getTotalMemory() {
            return totalMemory;
        }

        public long getUsedMemory() {
            return usedMemory;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            /* Total number of processors or cores available to the JVM */
            sb.append("Available processors (cores): \t" + getAvailableProcessors());
            sb.append(System.lineSeparator());

            /* Total amount of free memory available to the JVM */
            sb.append("Free memory (Mb): \t" +
                    getFreeMemory());
            sb.append(System.lineSeparator());

            /* This will return Long.MAX_VALUE if there is no preset limit */
            /* Maximum amount of memory the JVM will attempt to use */
            sb.append("Max memory (Mb): \t" + (getMaxMemory() == Long.MAX_VALUE / 1024 / 1024 ? "no limit" : getMaxMemory()));
            sb.append(System.lineSeparator());

            /* Total memory currently in use by the JVM */
            sb.append("Total memory (Mb): \t" + getTotalMemory());
            sb.append(System.lineSeparator());

            /* Used memory currently in use by the JVM */
            sb.append("Used memory (Mb): \t" + getUsedMemory());
            sb.append(System.lineSeparator());

            return sb.toString();
        }
    }
}
