package org.whitesource.agent.utils;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.dependency.resolver.DependencyCollector;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author raz.nitzan
 */
public class CommandLineProcess {
    /* --- Members --- */
    private String rootDirectory;
    private String[] args;
    private long timeoutReadLineSeconds;
    private long timeoutProcessMinutes;
    private boolean errorInProcess = false;
    private Process processStart = null;

    /* --- Statics Members --- */
    private static final long DEFAULT_TIMEOUT_READLINE_SECONDS = 60;
    private static final long DEFAULT_TIMEOUT_PROCESS_MINUTES = 15;
    private final Logger logger = LoggerFactory.getLogger(org.whitesource.agent.utils.CommandLineProcess.class);

    public CommandLineProcess(String rootDirectory, String[] args) {
        this.rootDirectory = rootDirectory;
        this.args = args;
        this.timeoutReadLineSeconds = DEFAULT_TIMEOUT_READLINE_SECONDS;
        this.timeoutProcessMinutes = DEFAULT_TIMEOUT_PROCESS_MINUTES;
    }

    public List<String> executeProcess() throws IOException {
        return executeProcess(true);
    }

    private List<String> executeProcess(boolean includeOutput) throws IOException {
        List<String> lines = new LinkedList<>();
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(new File(rootDirectory));
        // redirect the error output to avoid output of npm ls by operating system
        String redirectErrorOutput = DependencyCollector.isWindows() ? "nul" : "/dev/null";
        pb.redirectError(new File(redirectErrorOutput));
        if (!includeOutput) {
            pb.redirectOutput(new File(redirectErrorOutput));
        }
        logger.debug("start execute command '{}' in '{}'", String.join(" ", args), rootDirectory);
        this.processStart = pb.start();
        if (includeOutput) {
            InputStreamReader inputStreamReader = null;
            BufferedReader reader = null;
            ExecutorService executorService = Executors.newFixedThreadPool(1);
            boolean continueReadingLines = true;
            try {
                inputStreamReader = new InputStreamReader(this.processStart.getInputStream());
                reader = new BufferedReader(inputStreamReader);
                logger.debug("trying to read lines using '{}'", args);
                int lineIndex = 1;
                String line = "";
                while (continueReadingLines && line != null) {
                    Future<String> future = executorService.submit(new CommandLineProcess.ReadLineTask(reader));
                    try {
                        line = future.get(this.timeoutReadLineSeconds, TimeUnit.SECONDS);
                        if (StringUtils.isNotBlank(line)) {
                            logger.debug("Read line #{}: {}", lineIndex, line);
                            lines.add(line);
                        } else {
                            logger.debug("Finished reading {} lines", lineIndex - 1);
                        }
                    } catch (TimeoutException e) {
                        logger.debug("Received timeout when reading line #" + lineIndex, e);
                        continueReadingLines = false;
                        this.errorInProcess = true;
                    } catch (Exception e) {
                        logger.debug("Error reading line #" + lineIndex, e);
                        continueReadingLines = false;
                        this.errorInProcess = true;
                    }
                    lineIndex++;
                }
            } catch (Exception e) {
                logger.error("error parsing output : {}", e.getMessage());
            } finally {
                executorService.shutdown();
                IOUtils.closeQuietly(inputStreamReader);
                IOUtils.closeQuietly(reader);
            }
        }
        try {
            this.processStart.waitFor(this.timeoutProcessMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            this.errorInProcess = true;
            logger.error("'{}' was interrupted {}", args, e);
        }
        if (this.processStart.exitValue() != 0) {
            this.errorInProcess = true;
        }
        return lines;
    }

    public void executeProcessWithoutOutput() throws IOException {
        executeProcess(false);
    }

    public void setTimeoutReadLineSeconds(long timeoutReadLineSeconds) {
        this.timeoutReadLineSeconds = timeoutReadLineSeconds;
    }

    public void setTimeoutProcessMinutes(long timeoutProcessMinutes) {
        this.timeoutProcessMinutes = timeoutProcessMinutes;
    }

    public boolean isErrorInProcess() {
        return this.errorInProcess;
    }

    public int getExitStatus() {
        if (processStart != null) {
            return processStart.exitValue();
        }
        return 0;
    }

        /* --- Nested classes --- */

    class ReadLineTask implements Callable<String> {

        /* --- Members --- */

        private final BufferedReader reader;

        /* --- Constructors --- */

        ReadLineTask(BufferedReader reader) {
            this.reader = reader;
        }

        /* --- Overridden methods --- */

        @Override
        public String call() throws Exception {
            return reader.readLine();
        }
    }
}
