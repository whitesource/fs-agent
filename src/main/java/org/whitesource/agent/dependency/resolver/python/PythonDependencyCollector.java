package org.whitesource.agent.dependency.resolver.python;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.agent.utils.FilesUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author raz.nitzan
 */
public class PythonDependencyCollector extends DependencyCollector {

    /* -- Members -- */

    private boolean installVirtualEnv;
    private boolean resolveHierarchyTree;
    private boolean ignorePipInstallErrors;
    private String requirementsTxtPath;
    private String pythonPath;
    private String pipPath;
    private String tempDirPackages;
    private String tempDirVirtualenv;
    private String topLevelFolder;
    private AtomicInteger counterFolders = new AtomicInteger(0);

    private final Logger logger = LoggerFactory.getLogger(org.whitesource.agent.dependency.resolver.python.PythonDependencyResolver.class);

    /* --- Static members --- */

    private static final String VIRTUALENV = "virtualenv";
    private static final String USER = "--user";
    private static final String SOURCE = "source";
    private static final String BIN_ACTIVATE = "/env/bin/activate";
    private static final String SCRIPTS_ACTIVATE = "\\env\\Scripts\\activate.bat";
    private static final String AND = "&&";
    private static final String PIPDEPTREE = "pipdeptree";
    private static final String M = "-m";
    private static final String ENV = "/env";
    private static final String JSON_TREE = "--json-tree";
    private static final String HIERARCHY_TREE_TXT = "/HierarchyTree.txt";
    private static final String PACKAGE_NAME = "package_name";
    private static final String INSTALLED_VERSION = "installed_version";
    private static final String DEPENDENCIES = "dependencies";
    private static final String LOWER_COMMA = "_";
    private static final String F = "-f";
    private static final String COMMA = "-";
    private static final String EMPTY_STRING = "";
    private static final String DOWNLOAD = "download";
    private static final String INSTALL = "install";
    private static final String R_PARAMETER = "-r";
    private static final String D_PARAMETER = "-d";
    private static final String COMMENT_SIGN_PYTHON = "#";
    private static final int NUM_THREADS = 8;
    private static final String FORWARD_SLASH = "/";
    private static final String SCRIPT_SH = "/script.sh";
    private static final String BIN_BASH = "#!/bin/bash";

    /* --- Constructors --- */

    public PythonDependencyCollector(String pythonPath, String pipPath, boolean installVirtualEnv, boolean resolveHierarchyTree, boolean ignorePipInstallErrors,
                                     String requirementsTxtPath, String tempDirPackages, String tempDirVirtualEnv) {
        super();
        this.pythonPath = pythonPath;
        this.pipPath = pipPath;
        this.installVirtualEnv = installVirtualEnv;
        this.resolveHierarchyTree = resolveHierarchyTree;
        this.requirementsTxtPath = requirementsTxtPath;
        this.tempDirPackages = tempDirPackages;
        this.tempDirVirtualenv = tempDirVirtualEnv;
        this.ignorePipInstallErrors = ignorePipInstallErrors;
    }

    @Override
    public Collection<AgentProjectInfo> collectDependencies(String topLevelFolder) {
        this.topLevelFolder = topLevelFolder;
        List<DependencyInfo> dependencies = new LinkedList<>();
        boolean virtualEnvInstalled = true;
        if (this.installVirtualEnv && this.resolveHierarchyTree) {
            try {
                // install virtualEnv package
                virtualEnvInstalled = !processCommand(new String[]{pythonPath, M, pipPath, INSTALL, USER, VIRTUALENV}, true);
            } catch (IOException e) {
                virtualEnvInstalled = false;
            }
        }
        // FSA will run 'pip download -r requirements.txt -d TEMP_FOLDER_PATH'
        if (virtualEnvInstalled) {
            try {
                logger.debug("Collecting python dependencies. It might take a few minutes.");
                boolean failedGetTree;
                boolean failed = processCommand(new String[]{pipPath, DOWNLOAD, R_PARAMETER, this.requirementsTxtPath, D_PARAMETER, tempDirPackages}, true);
                // if process failed, download each package line by line
                if (failed) {
                    String error = "Fail to run 'pip install -r " + this.requirementsTxtPath + "'";
                    logger.warn(error + ". To see the full error, re-run the plugin with this parameter in the config file: log.level=debug");
                } else if (!failed && !this.resolveHierarchyTree) {
                    dependencies = collectDependencies(new File(tempDirPackages), this.requirementsTxtPath);
                } else if (!failed && this.resolveHierarchyTree) {
                    failedGetTree = getTree(this.requirementsTxtPath);
                    if (!failedGetTree) {
                        dependencies = collectDependenciesWithTree(this.tempDirVirtualenv + HIERARCHY_TREE_TXT, requirementsTxtPath);
                    } else {
                        // collect flat list if hierarchy tree failed
                        dependencies = collectDependencies(new File(tempDirPackages), this.requirementsTxtPath);
                    }
                }
                if (failed && this.ignorePipInstallErrors) {
                    logger.info("Try to download each dependency in the requirements.txt file one by one. It might take a few minutes.");
                    FilesUtils.deleteDirectory(new File(tempDirPackages));
                    this.tempDirPackages = new FilesUtils().createTmpFolder(false, PythonDependencyResolver.WHITESOURCE_PYTHON_TEMP_FOLDER);
                    if (this.tempDirPackages != null) {
                        downloadLineByLine(this.requirementsTxtPath);
                        dependencies = collectDependencies(new File(tempDirPackages), this.requirementsTxtPath);
                        FilesUtils.deleteDirectory(new File(tempDirPackages));
                    }
                }
            } catch (IOException e) {
                logger.warn("Cannot read the requirements.txt file");
            }
        } else {
            logger.warn("Virutalenv package installation failed");
        }
        return getSingleProjectList(dependencies);
    }

    private List<DependencyInfo> collectDependenciesWithTree(String treeFile, String requirementsTxtPath) {
        List<DependencyInfo> dependencies = new LinkedList<>();
        try {
            // read json dependency tree from cmd tmp file
            String allTreeFile = new String(Files.readAllBytes(Paths.get(treeFile)), StandardCharsets.UTF_8);
            JSONArray treeArray = new JSONArray(allTreeFile);
            File[] files = (new File(this.tempDirPackages)).listFiles();
            dependencies = collectDependenciesReq(treeArray, files, requirementsTxtPath);
        } catch (IOException e) {
            logger.warn("Cannot read the hierarchy tree file");
        }
        return dependencies;
    }

    private List<DependencyInfo> collectDependenciesReq(JSONArray dependenciesArray, File[] files, String requirementsTxtPath) {
        List<DependencyInfo> dependencies = new LinkedList<>();
        for (int i = 0; i < dependenciesArray.length(); i++) {
            JSONObject packageObject = dependenciesArray.getJSONObject(i);
            DependencyInfo dependency = getDependencyByName(files, packageObject.getString(PACKAGE_NAME), packageObject.getString(INSTALLED_VERSION), requirementsTxtPath);
            if (dependency != null) {
                dependencies.add(dependency);
                dependency.setChildren(collectDependenciesReq(packageObject.getJSONArray(DEPENDENCIES), files, requirementsTxtPath));
            }
        }
        return dependencies;
    }

    private DependencyInfo getDependencyByName(File[] files, String name, String version, String requirementsTxtPath) {
        String nameAndVersion1 = name + COMMA + version;
        String nameAndVersion2 = name.replace(COMMA, LOWER_COMMA) + COMMA + version;
        nameAndVersion1 = nameAndVersion1.toLowerCase();
        nameAndVersion2 = nameAndVersion2.toLowerCase();
        for (File file : files) {
            String fileNameLowerCase = file.getName().toLowerCase();
            if (fileNameLowerCase.startsWith(nameAndVersion1) || fileNameLowerCase.startsWith(nameAndVersion2)) {
                return getDependencyFromFile(file, requirementsTxtPath);
            }
        }
        return null;
    }

    private boolean getTree(String requirementsTxtPath) {
        boolean failed;
        try {
            // Create the virtual environment
            failed = processCommand(new String[]{pythonPath, M, VIRTUALENV, this.tempDirVirtualenv + ENV}, true);
            if (!failed) {
                if (isWindows()) {
                    failed = processCommand(getFullCmdInstallation(requirementsTxtPath), true);
                } else {
                    String scriptPath = createScript(requirementsTxtPath);
                    if (scriptPath != null) {
                        failed = processCommand(new String[] {scriptPath}, true);
                    } else {
                        failed = true;
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Cannot install requirements.txt in the virtual environment.");
            failed = true;
        }
        return failed;
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

    private String[] getFullCmdInstallation(String requirementsTxtPath) {
        // execute all the command with and between them in order to save the virtualenv shell
        String[] windowsCommand = new String[]{this.tempDirVirtualenv + SCRIPTS_ACTIVATE, AND, pipPath, INSTALL, R_PARAMETER,
                requirementsTxtPath, F, this.tempDirPackages, AND, pipPath, INSTALL, PIPDEPTREE, AND, PIPDEPTREE,
                JSON_TREE, ">", this.tempDirVirtualenv + HIERARCHY_TREE_TXT};
        return windowsCommand;
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
            if (processCommand(new String[]{pipPath, DOWNLOAD, packageName, D_PARAMETER, tempDirPackages + FORWARD_SLASH + currentCounter}, false)) {
                logger.warn(message + packageName + "'");
            }
        } catch (IOException e) {
            logger.warn("Cannot read the requirements.txt file");
        }
    }

    private String createScript(String requirementsTxtPath) {
        FilesUtils filesUtils = new FilesUtils();
        String path = filesUtils.createTmpFolder(false, PythonDependencyResolver.WHITESOURCE_PYTHON_TEMP_FOLDER);
        String scriptPath = null;
        if (path != null) {
            scriptPath = path + SCRIPT_SH;
            try {
                File file = new File(scriptPath);
                FileOutputStream fos = new FileOutputStream(file);
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(fos));
                bufferedWriter.write(BIN_BASH);
                bufferedWriter.newLine();
                bufferedWriter.write(SOURCE + Constants.WHITESPACE + this.tempDirVirtualenv + BIN_ACTIVATE);
                bufferedWriter.newLine();
                bufferedWriter.write(pipPath + Constants.WHITESPACE + INSTALL + Constants.WHITESPACE + R_PARAMETER +
                        Constants.WHITESPACE + requirementsTxtPath + Constants.WHITESPACE + F + Constants.WHITESPACE + this.tempDirPackages);
                bufferedWriter.newLine();
                bufferedWriter.write(pipPath + Constants.WHITESPACE + INSTALL + Constants.WHITESPACE + PIPDEPTREE);
                bufferedWriter.newLine();
                bufferedWriter.write(PIPDEPTREE + Constants.WHITESPACE + JSON_TREE + Constants.WHITESPACE + ">" + Constants.WHITESPACE + this.tempDirVirtualenv + HIERARCHY_TREE_TXT);
                bufferedWriter.close();
                fos.close();
                file.setExecutable(true);
            } catch (IOException e) {
                return null;
            }
        }
        return scriptPath;
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