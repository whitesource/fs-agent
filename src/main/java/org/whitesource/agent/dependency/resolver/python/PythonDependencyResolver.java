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
package org.whitesource.agent.dependency.resolver.python;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.agent.utils.FilesUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class PythonDependencyResolver extends AbstractDependencyResolver {

    /* -- Members -- */

    private final String pythonPath;
    private final String pipPath;
    private final Collection<String> excludes = Arrays.asList("**/*" + PY_EXT);
    private String tempDir;
    private AtomicInteger counterFolders = new AtomicInteger(0);
    private String topLevelFolder;
    boolean ignorePipInstallErrors;

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(org.whitesource.agent.dependency.resolver.python.PythonDependencyResolver.class);
    private static final String PATTERN = "**/*";
    private static final String PYTHON_BOM = "requirements.txt";
    private static final String WHITESOURCE_TEMP_FOLDER = "Whitesource_python_resolver";

    private static final String PY_EXT = ".py";
    private final String JAVA_TEMP_DIR = System.getProperty("java.io.tmpdir");

    private static final String DOWNLOAD = "download";
    private static final String R_PARAMETER = "-r";
    private static final String D_PARAMETER = "-d";
    private static final String COMMA = "-";
    private static final String TAR_GZ = ".tar.gz";
    private static final String EMPTY_STRING = "";
    private static final String DOT = ".";
    private static final String COMMENT_SIGN_PYTHON = "#";
    private static final int NUM_THREADS = 8;


    /* --- Constructors --- */
    public PythonDependencyResolver(String pythonPath, String pipPath, boolean ignorePipInstallErrors) {
        super();
        this.pythonPath = pythonPath;
        this.pipPath = pipPath;
        this.ignorePipInstallErrors = ignorePipInstallErrors;
    }

    /* --- Getters / Setters --- */

    public String getPythonPath() {
        return pythonPath;
    }

    public String getPipPath() {
        return pipPath;
    }

    public String getTopLevelFolder() {
        return topLevelFolder;
    }

    public void setTopLevelFolder(String topLevelFolder) {
        this.topLevelFolder = topLevelFolder;
    }

    @Override
    public ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> configFiles) {
        Map<AgentProjectInfo, Path> resolvedProjects = new HashMap<>();
        setTopLevelFolder(topLevelFolder);
        String requirementsTxtPath = Paths.get(topLevelFolder + FORWARD_SLASH + PYTHON_BOM).toAbsolutePath().toString();
        boolean folderWasCreated = createTmpFolder();

        // FSA will run 'pip download -r requirements.txt -d TEMP_FOLDER_PATH'
        List<DependencyInfo> dependencies = new LinkedList<>();
        if (folderWasCreated) {
            try {
                boolean processFailed = processCommand(new String[]{pipPath, DOWNLOAD, R_PARAMETER, requirementsTxtPath, D_PARAMETER, tempDir}, true);
                // if process failed, download each package line by line
                if (processFailed) {
                    String error = "Fail to run 'pip install -r " + requirementsTxtPath + "'";
                    logger.warn(error + ". To see the full error, re-run the plugin with this parameter in the config file: log.level=debug");
                } else {
                    dependencies = collectDependencies(new File(tempDir), requirementsTxtPath);
                }
                if (processFailed && this.ignorePipInstallErrors) {
                    logger.info("Try to download each dependency in the requirements.txt file one by one. It might take a few minutes.");
                    FilesUtils.deleteDirectory(new File(tempDir));
                    createTmpFolder();
                    downloadLineByLine(requirementsTxtPath);
                    dependencies = collectDependencies(new File(tempDir), requirementsTxtPath);
                }
            } catch (IOException e) {
                logger.warn("Cannot read the requirements.txt file");
            }
            FilesUtils.deleteDirectory(new File(tempDir));
        }
        AgentProjectInfo project = new AgentProjectInfo();
        project.setDependencies(dependencies);
        resolvedProjects.put(project, null);
        return new ResolutionResult(resolvedProjects, getExcludes(), DependencyType.PYTHON, topLevelFolder);
    }

    private boolean createTmpFolder() {
        this.tempDir = getTempDir();
        boolean createFolder = true;
        try {
            FileUtils.forceMkdir(new File(tempDir));
        } catch (IOException e) {
            logger.warn("Failed to create temp folder : " + e.getMessage());
            createFolder = false;
        }
        return createFolder;
    }

    private void downloadLineByLine(String requirementsTxtPath) {
        try {
            ExecutorService executorService = Executors.newWorkStealingPool(NUM_THREADS);
            Collection<DownloadDependency> threadsCollection = new LinkedList<>();
            BufferedReader bufferedReader = new BufferedReader(new FileReader(new File(requirementsTxtPath)));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (StringUtils.isNotEmpty(line)) {
                    int commentIndex = line.indexOf(COMMENT_SIGN_PYTHON);
                    if (commentIndex < 0) {
                        commentIndex = line.length();
                    }
                    String packageNameToDownload = line.substring(0, commentIndex);
                    if (StringUtils.isNotEmpty(packageNameToDownload)) {
                        threadsCollection.add(new DownloadDependency(packageNameToDownload));
                    }
                }
            }
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            runThreadCollection(executorService, threadsCollection);
        } catch (IOException e) {
            logger.warn("Cannot read the requirements.txt file: {}", e.getMessage());
        }
    }

    private void runThreadCollection(ExecutorService executorService, Collection<DownloadDependency> threadsCollection) {
        try {
            executorService.invokeAll(threadsCollection);
            executorService.shutdown();
        } catch (InterruptedException e) {
            logger.warn("One of the threads was interrupted, please try to scan again the project. Error: {}", e.getMessage());
            logger.debug("One of the threads was interrupted, please try to scan again the project. Error: {}", e.getStackTrace());
        }
    }

    private void downloadOneDependency(String packageName) {
        int currentCounter = this.counterFolders.incrementAndGet();
        String message = "Failed to download the transitive dependencies of '";
        try {
            if (processCommand(new String[]{pipPath, DOWNLOAD, packageName, D_PARAMETER, tempDir + FORWARD_SLASH + currentCounter}, false)) {
                logger.warn(message + packageName + "'");
            }
        } catch (IOException e) {
            logger.warn("Cannot read the requirements.txt file");
        }
    }

    private boolean processCommand(String[] args, boolean withOutput) throws IOException {
        CommandLineProcess commandLineProcess = new CommandLineProcess(this.topLevelFolder, args);
        if (withOutput) {
            commandLineProcess.executeProcess();
        } else {
            commandLineProcess.executeProcessWithoutOutput();
        }
        return commandLineProcess.isErrorInProcess();
    }

    private String getTempDir() {
        String creationDate = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String tempFolder = JAVA_TEMP_DIR.endsWith(File.separator) ? JAVA_TEMP_DIR + WHITESOURCE_TEMP_FOLDER + File.separator + creationDate :
                JAVA_TEMP_DIR + File.separator + WHITESOURCE_TEMP_FOLDER + File.separator + creationDate;

        return tempFolder;
    }

    private List<DependencyInfo> collectDependencies(File folder, String requirementsTxtPath) {
        List<DependencyInfo> result = new LinkedList<>();
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                for (File regFile : file.listFiles()) {
                    addDependencyInfoData(regFile, requirementsTxtPath, result);
                }
            } else {
                addDependencyInfoData(file, requirementsTxtPath, result);
            }
        }
        return result;
    }

    private void addDependencyInfoData(File file, String requirementsTxtPath, List<DependencyInfo> dependencies) {
        DependencyInfo dependency = getDependencyFromFile(file, requirementsTxtPath);
        if (dependency != null) {
            dependencies.add(dependency);
        }
    }

    private DependencyInfo getDependencyFromFile(File file, String requirementsTxtPath) {
        DependencyInfo dependency = new DependencyInfo();
        String fileName = file.getName();
        // ignore name and version and use only the sha1

//        int firstIndexOfComma = fileName.indexOf(COMMA);
//        String name = fileName.substring(0, firstIndexOfComma);
//        int indexOfExtension = fileName.indexOf(TAR_GZ);
//        String version;
//        if (indexOfExtension < 0) {
//            indexOfExtension = fileName.lastIndexOf(DOT);
//            version = fileName.substring(firstIndexOfComma + 1, indexOfExtension);
//            int indexOfComma = version.indexOf(COMMA);
//            if (indexOfComma >= 0) {
//                version = version.substring(0, indexOfComma);
//            }
//        } else {
//            version = fileName.substring(firstIndexOfComma + 1, indexOfExtension);
//        }
        String sha1 = getSha1(file);
        if (StringUtils.isEmpty(sha1)) {
            return null;
        }
//        dependency.setGroupId(name);
        dependency.setArtifactId(fileName);
//        dependency.setVersion(version);
        dependency.setSha1(sha1);
        dependency.setSystemPath(requirementsTxtPath);
        dependency.setDependencyType(DependencyType.PYTHON);
        return dependency;
    }

    private String getSha1(File file) {
        try {
            return ChecksumUtils.calculateSHA1(file);
        } catch (IOException e) {
            logger.warn("Failed getting " + file + ". File will not be send to WhiteSource server.");
            return EMPTY_STRING;
        }
    }

    @Override
    protected Collection<String> getExcludes() {
        return excludes;
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return new ArrayList<>(Arrays.asList(PY_EXT));
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.PYTHON;
    }

    @Override
    public String getBomPattern() {
        return PATTERN + PYTHON_BOM;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }

    /* --- Nested classes --- */

    class DownloadDependency implements Callable<Void> {

        /* --- Members --- */

        private String packageName;

        /* --- Constructors --- */

        public DownloadDependency(String packageName) {
            this.packageName = packageName;
        }

        /* --- Overridden methods --- */

        @Override
        public Void call() {
            downloadOneDependency(this.packageName);
            return null;
        }
    }
}
