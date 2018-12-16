package org.whitesource.agent.dependency.resolver.python;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.TempFolders;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.utils.CommandLineProcess;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author raz.nitzan
 */
public class PythonDependencyCollector extends DependencyCollector {
    /* -- Members -- */

    private boolean installVirtualEnv;
    private boolean resolveHierarchyTree;
    private boolean ignorePipInstallErrors;
    private boolean ignorePipEnvInstallErrors;
    private boolean runPipEnvPreStep;
    private boolean pipenvInstallDevDependencies;
    private String requirementsTxtOrSetupPyPath;
    private String pythonPath;
    private String pipPath;
    private String tempDirPackages;
    private String tempDirVirtualenv;
    private String topLevelFolder;
    private AtomicInteger counterFolders = new AtomicInteger(0);
    private DependenciesFileType dependencyFileType;
    private String tempDirDirectPackages;


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
    private static final String ARROW = ">";
    private static final String NO_DEPS = "--no-deps";
    private static final String[] DEFAULT_PACKAGES_IN_PIPDEPTREE = new String[] {PIPDEPTREE, "setuptools", "wheel"};
    private static final String APOSTROPHE = "'";
    private static final String PIPENV = "pipenv";
    private static final String GRAPH = "graph";
    private static final String RUN = "run";
    private static final String DEV = "--dev";
    private static final String LOCK = "lock";
    /* --- Constructors --- */

    public PythonDependencyCollector(String pythonPath, String pipPath, boolean installVirtualEnv, boolean resolveHierarchyTree, boolean ignorePipInstallErrors,
                                     String requirementsTxtOrSetupPyPath, String tempDirPackages, String tempDirVirtualEnv, String tempDirDirectPackages) {
        super();
        this.pythonPath = pythonPath;
        this.pipPath = pipPath;
        this.installVirtualEnv = installVirtualEnv;
        this.resolveHierarchyTree = resolveHierarchyTree;
        if (requirementsTxtOrSetupPyPath.endsWith(Constants.SETUP_PY)) {
            requirementsTxtOrSetupPyPath = requirementsTxtOrSetupPyPath.substring(0, requirementsTxtOrSetupPyPath.length() - (Constants.SETUP_PY.length() + 1));
            this.dependencyFileType = DependenciesFileType.SETUP_PY;
        } else {
            this.dependencyFileType = DependenciesFileType.REQUIREMENTS_TXT;
        }
        this.requirementsTxtOrSetupPyPath = requirementsTxtOrSetupPyPath;
        this.tempDirPackages = tempDirPackages;
        this.tempDirVirtualenv = tempDirVirtualEnv;
        this.tempDirDirectPackages = tempDirDirectPackages;
        this.ignorePipInstallErrors = ignorePipInstallErrors;
    }

    public PythonDependencyCollector(boolean ignorePipEnvInstallErrors, boolean runPipEnvPreStep, String tempDirPackages, String pythonPath, String pipPath, boolean pipenvInstallDevDependencies) {
        super();
        this.pythonPath = pythonPath;
        this.pipPath = pipPath;
        this.ignorePipEnvInstallErrors = ignorePipEnvInstallErrors;
        this.dependencyFileType = DependenciesFileType.PIPFILE;
        this.tempDirPackages = tempDirPackages;
        this.runPipEnvPreStep = runPipEnvPreStep;
        this.pipenvInstallDevDependencies = pipenvInstallDevDependencies;
    }

    @Override
    public Collection<AgentProjectInfo> collectDependencies(String topLevelFolder) {
        this.topLevelFolder = topLevelFolder;
        List<DependencyInfo> dependencies;
        if (this.dependencyFileType.equals(DependenciesFileType.PIPFILE)) {
            logger.debug("Found pipfile, running pipenv algorithm");
           dependencies = runPipEnvAlgorithm();
        } else {
            logger.debug("Found requiremrents or setup file, running pip algorithm");
           dependencies = runPipAlgorithm();
        }
        return getSingleProjectList(dependencies);
    }

    private List<DependencyInfo> runPipAlgorithm() {
        List<DependencyInfo> dependencies = new LinkedList<>();
        boolean failed = false;
        boolean virtualEnvInstalled = true;
        if (this.installVirtualEnv && this.resolveHierarchyTree) {
            try {
                // install virtualEnv package
                virtualEnvInstalled = !processCommand(new String[]{pythonPath, M, pipPath, INSTALL, USER, VIRTUALENV}, true);
            } catch (IOException e) {
                virtualEnvInstalled = false;
            }
        }
        //FSA will run 'pip download -r requirements.txt -d TEMP_FOLDER_PATH'
        if (virtualEnvInstalled) {
            try {
                logger.debug("Collecting python dependencies. It might take a few minutes.");
                boolean failedGetTree;
                if (this.dependencyFileType == DependenciesFileType.REQUIREMENTS_TXT) {
                    failed = processCommand(new String[]{pipPath, DOWNLOAD, R_PARAMETER, this.requirementsTxtOrSetupPyPath, D_PARAMETER, tempDirPackages}, true);
                } else if (this.dependencyFileType == DependenciesFileType.SETUP_PY) {
                    failed = processCommand(new String[]{pipPath, DOWNLOAD, this.requirementsTxtOrSetupPyPath, D_PARAMETER, tempDirPackages}, true);
                }
                if (failed) {
                    String error = null;
                    if (this.dependencyFileType == DependenciesFileType.REQUIREMENTS_TXT) {
                        error = "Fail to run 'pip install -r " + this.requirementsTxtOrSetupPyPath + APOSTROPHE;
                    } else if (this.dependencyFileType == DependenciesFileType.SETUP_PY) {
                        error = "Fail to run 'pip install " + this.requirementsTxtOrSetupPyPath + APOSTROPHE;
                    }
                    logger.warn("{}. To see the full error, re-run the plugin with this parameter in the config file: log.level=debug", error);
                } else if (!failed && !this.resolveHierarchyTree) {
                    dependencies = collectDependencies(new File(tempDirPackages), this.requirementsTxtOrSetupPyPath);
                } else if (!failed && this.resolveHierarchyTree) {
                    failedGetTree = getTree(this.requirementsTxtOrSetupPyPath);
                    if (!failedGetTree) {
                        dependencies = collectDependenciesWithTree(this.tempDirVirtualenv + HIERARCHY_TREE_TXT, requirementsTxtOrSetupPyPath);
                        // the library pipdeptree removes the direct dependencies if those dependencies are transitive dependencies in other dependencies. fixDependencies() returns the direct dependencies
                        // This issue is not relevant to setup.py dependency file type
                        if (this.dependencyFileType == DependenciesFileType.REQUIREMENTS_TXT) {
                            fixDependencies(dependencies);
                        }
                    } else {
                        // collect flat list if hierarchy tree failed
                        dependencies = collectDependencies(new File(tempDirPackages), this.requirementsTxtOrSetupPyPath);
                    }
                }
                // If there was an error and the dependency file type is requirements.txt, download each dependency in the requirements.txt file one by one
                if (failed && this.ignorePipInstallErrors && this.dependencyFileType == DependenciesFileType.REQUIREMENTS_TXT) {
                    logger.info("Try to download each dependency in " + this.requirementsTxtOrSetupPyPath + " file one by one. It might take a few minutes.");
                    FilesUtils.deleteDirectory(new File(tempDirPackages));
                    this.tempDirPackages = new FilesUtils().createTmpFolder(false, TempFolders.UNIQUE_PYTHON_TEMP_FOLDER);
                    if (this.tempDirPackages != null) {
                        downloadLineByLine(this.requirementsTxtOrSetupPyPath);
                        dependencies = collectDependencies(new File(tempDirPackages), this.requirementsTxtOrSetupPyPath);
                        FilesUtils.deleteDirectory(new File(tempDirPackages));
                    }
                }
            } catch (IOException e) {
                logger.warn("Cannot read the requirements.txt file");
            }
        } else {
            logger.warn("Virutalenv package installation failed");
        }
        return dependencies;
    }

    private List<DependencyInfo> runPipEnvAlgorithm() {
        List<DependencyInfo> dependencies = new LinkedList<>();
        boolean failed ;
        Set<String> dependencyNamesVersions;
        // if to run pipenv install
        if (runPipEnvPreStep) {
            // if to install dev dependencies or not
            logger.debug("Running PipEnv PreStep");
            if (pipenvInstallDevDependencies) {
                runPipEnvInstallCommand(new String[]{PIPENV, INSTALL, DEV});
            } else {
                runPipEnvInstallCommand(new String[]{PIPENV, INSTALL});
            }
        }
        logger.info("Running dependency tree with 'pipenv graph'");
        //create requirements.txt temp file in order to be able to download packages
        String requirementsTempFile = tempDirPackages + Constants.BACK_SLASH + Constants.PYTHON_REQUIREMENTS;
        List<String> lines;
        lines = commandLineRunner(topLevelFolder, new String[]{PIPENV, GRAPH});
        if (!CollectionUtils.isEmpty(lines)) {
            logger.info("Parsing dependency tree");
            dependencyNamesVersions = parsePipEnvGraph(lines);
        } else {
            // (pipfile lock -r , pipfile lock -r --dev) = pipenv graph
            //pipenv graph picks from the virtual environment, pipfile lock picks dependencies from the pipfile.lock
            logger.warn("pipenv graph failed, getting dependencies directly from pipfile");
            lines = commandLineRunner(topLevelFolder, new String[]{PIPENV, LOCK, R_PARAMETER, DEV});
            List<String> lines1 = commandLineRunner(topLevelFolder, new String[]{PIPENV, LOCK, R_PARAMETER});
            lines.addAll(lines1);
            dependencyNamesVersions = parsePipFile(lines);
        }

        if (dependencyNamesVersions != null) {
            try (FileWriter fw = new FileWriter(new File(requirementsTempFile))) {
                for (String dependencyNamesVersion : dependencyNamesVersions) {
                    fw.write(dependencyNamesVersion + "\n");
                }
            } catch (IOException e) {
                logger.warn("Cannot create a file to write in temp folder {}", e.getMessage());
                logger.debug("Cannot create a file to write in temp folder, Error: {}", e.getStackTrace());
            }
        } else {
            logger.warn("pipenv graph anad pipfile.lock failed please try to turn python.runPipenvPreStep to run pipenv install");
        }
        //create a requirements.txt file from parsing pipenv graph
        try {
            logger.info("downloading packages");
            failed = processCommand(new String[]{PIPENV, RUN, pipPath, DOWNLOAD, R_PARAMETER, requirementsTempFile, D_PARAMETER, tempDirPackages}, true);
            if (failed && ignorePipEnvInstallErrors) {
                logger.info("Failed to download all dependencies at once, Try to install dependencies one by one. It might take a few minutes.");
                for (String dependencyNamesVersion : dependencyNamesVersions) {
                    failed = processCommand(new String[]{PIPENV, RUN, pipPath, DOWNLOAD, dependencyNamesVersion, D_PARAMETER, tempDirPackages}, true);
                    if (failed) {
                        logger.warn("pipenv run pip download {} failed to execute", dependencyNamesVersion);
                    }
                }
            }
            dependencies = collectDependencies(new File(tempDirPackages), topLevelFolder);
        } catch (IOException e) {
            logger.warn("downloading dependencies failed: {}", e.getMessage());
            logger.debug("downloading dependencies failed: {}", e.getStackTrace());
        }
        return dependencies;
    }

    private Set<String> parsePipEnvGraph(List<String> lines) {
        Set<String> dependencyLines = new HashSet<>();
        for (String line : lines) {
            logger.debug(line);
            if(line.contains(Constants.DOUBLE_EQUALS)) {
                dependencyLines.add(line);
            } else {
                //- execnet [required: >=1.1, installed: 1.5.0] -> convert to execnet==1.5.0
                String artifactId = line.substring(line.indexOf(Constants.DASH) + 2, line.indexOf(Constants.OPEN_SQUARE_BRACKET) - 1);
                String version = line.substring(line.lastIndexOf(Constants.WHITESPACE) + 1, line.indexOf(Constants.CLOSE_SQUARE_BRACKET));
                dependencyLines.add(artifactId + Constants.DOUBLE_EQUALS + version);
            }
        }
        return dependencyLines;
    }

    private void fixDependencies(Collection<DependencyInfo> dependencies) {
        logger.debug("Trying to get all the direct dependencies.");
        boolean failed = false;
        try {
            // get direct dependencies
            if (this.dependencyFileType == DependenciesFileType.REQUIREMENTS_TXT) {
                failed = processCommand(new String[]{pipPath, DOWNLOAD, R_PARAMETER, this.requirementsTxtOrSetupPyPath, NO_DEPS, D_PARAMETER, tempDirDirectPackages}, true);
            }
            if (!failed) {
                findDirectDependencies(dependencies);
            } else {
                logger.debug("Cannot download direct dependencies.");
            }
        } catch (IOException e) {
            logger.debug("Cannot download direct dependencies.");
        }
    }

    private void findDirectDependencies(Collection<DependencyInfo> dependencies) {
        File directDependenciesFolder = new File(this.tempDirDirectPackages);
        File[] directDependenciesFiles = directDependenciesFolder.listFiles();
        if (directDependenciesFiles.length > dependencies.size()) {
            int missingDirectDependencies = directDependenciesFiles.length - dependencies.size();
            logger.debug("There are {} missing direct dependencies", missingDirectDependencies);
            int i = 0;
            while (missingDirectDependencies > 0) {
                boolean found = false;
                File directDependencyToCheck = directDependenciesFiles[i];
                for (DependencyInfo dependency : dependencies) {
                    if (dependency.getArtifactId().equals(directDependencyToCheck.getName())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    logger.debug("Trying to find the direct dependency of: {}", directDependencyToCheck.getName());
                    DependencyInfo foundDependencyInfo = findDirectDependencyInTree(dependencies, directDependencyToCheck);
                    if (foundDependencyInfo != null) {
                        dependencies.add(foundDependencyInfo);
                    } else {
                        // probably issue with pipdeptree (maybe cyclic dependency)
                        logger.warn("Error getting dependency {}, might be issues using pipdeptree command", directDependencyToCheck.getName());
                    }
                    missingDirectDependencies--;
                }
                i++;
            }
        }
    }

    private DependencyInfo findDirectDependencyInTree(Collection<DependencyInfo> dependencies, File directDependencyToCheck) {
        for (DependencyInfo dependency : dependencies) {
            if (dependency.getArtifactId().equals(directDependencyToCheck.getName())) {
                return dependency;
            } else {
                DependencyInfo child = findDirectDependencyInTree(dependency.getChildren(), directDependencyToCheck);
                if (child != null) {
                    return child;
                }
            }
        }
        return null;
    }

    private List<DependencyInfo> collectDependenciesWithTree(String treeFile, String requirementsTxtPath) {
        List<DependencyInfo> dependencies = new LinkedList<>();
        try {
            // read json dependency tree from cmd tmp file
            String allTreeFile = new String(Files.readAllBytes(Paths.get(treeFile)), StandardCharsets.UTF_8);
            JSONArray treeArray = new JSONArray(allTreeFile);
            File[] files = (new File(this.tempDirPackages)).listFiles();
            if (this.dependencyFileType == DependenciesFileType.REQUIREMENTS_TXT) {
                dependencies = collectDependenciesReq(treeArray, files, requirementsTxtPath);
            } else if (this.dependencyFileType == DependenciesFileType.SETUP_PY) {
                dependencies = collectDependenciesReq(treeArray.getJSONObject(findIndexInArrayOfPipdeptree(treeArray)).getJSONArray(DEPENDENCIES), files, requirementsTxtPath);
            }
        } catch (IOException e) {
            logger.warn("Cannot read the hierarchy tree file");
        }
        return dependencies;
    }

    private int findIndexInArrayOfPipdeptree(JSONArray treeArray) {
        for (int i = 0; i < treeArray.length(); i++) {
            boolean findDefault = false;
            String packageName = treeArray.getJSONObject(i).getString(PACKAGE_NAME);
            for (String defaultPackage : DEFAULT_PACKAGES_IN_PIPDEPTREE) {
                if (defaultPackage.equals(packageName)) {
                    findDefault = true;
                    break;
                }
            }
            if (!findDefault) {
                return i;
            }
        }
        return treeArray.length() - 1;
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
            logger.warn("Failed getting. {} File will not be send to WhiteSource server.", file);
            return EMPTY_STRING;
        }
    }

    private String[] getFullCmdInstallation(String requirementsTxtPath) {
        // execute all the command with and between them in order to save the virtualenv shell
        String[] windowsCommand = null;
        if (this.dependencyFileType == DependenciesFileType.REQUIREMENTS_TXT) {
            windowsCommand = new String[]{this.tempDirVirtualenv + SCRIPTS_ACTIVATE, AND, pipPath, INSTALL, R_PARAMETER,
                    requirementsTxtPath, F, this.tempDirPackages, AND, pipPath, INSTALL, PIPDEPTREE, AND, PIPDEPTREE,
                    JSON_TREE, ARROW, this.tempDirVirtualenv + HIERARCHY_TREE_TXT};
        } else if (this.dependencyFileType == DependenciesFileType.SETUP_PY) {
            windowsCommand = new String[]{this.tempDirVirtualenv + SCRIPTS_ACTIVATE, AND, pipPath, INSTALL,
                    requirementsTxtPath, F, this.tempDirPackages, AND, pipPath, INSTALL, PIPDEPTREE, AND, PIPDEPTREE,
                    JSON_TREE, ARROW, this.tempDirVirtualenv + HIERARCHY_TREE_TXT};
        }
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

    private List<String> commandLineRunner(String rootDirectory, String[] params) {
            try {
                // run pipenv graph
                CommandLineProcess commandLineProcess = new CommandLineProcess(rootDirectory, params);
                List<String> lines = commandLineProcess.executeProcess();
                if (commandLineProcess.isErrorInProcess()) {
                    logger.warn("error in process after running command {} on {}", params, rootDirectory);
                } else {
                    return lines;
                }
            } catch (IOException e) {
                    logger.warn("Error getting results after running command {} on {}, {}", params, rootDirectory, e.getMessage());
                    logger.debug("Error: {}", e.getStackTrace());
            }
            return Collections.emptyList();
    }

    private Set<String> parsePipFile(List<String> lines) {
        Set<String> DependencyLines = new HashSet<>();
        for (String line : lines) {
            if (!line.contains(Constants.WHITESPACE)) {
                DependencyLines.add(line);
            } else {
                if (line.contains(Constants.DOUBLE_EQUALS)) {
                    String artifactAndVersion = line.substring(0, line.indexOf(Constants.SEMI_COLON));
                    DependencyLines.add(artifactAndVersion);
                }
            }
        }
        return DependencyLines;
    }

    private void downloadLineByLine(String requirementsTxtPath) {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(new File(requirementsTxtPath)))){
            ExecutorService executorService = Executors.newWorkStealingPool(NUM_THREADS);
            Collection<DownloadDependency> threadsCollection = new LinkedList<>();
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
        String path = filesUtils.createTmpFolder(false, TempFolders.UNIQUE_PYTHON_TEMP_FOLDER);
        String scriptPath = null;
        if (path != null) {
            scriptPath = path + SCRIPT_SH;
            File file = new File(scriptPath);
            try (   FileOutputStream fos = new FileOutputStream(file);
                    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(fos))) {
                bufferedWriter.write(BIN_BASH);
                bufferedWriter.newLine();
                bufferedWriter.write(SOURCE + Constants.WHITESPACE + this.tempDirVirtualenv + BIN_ACTIVATE);
                bufferedWriter.newLine();
                if (this.dependencyFileType == DependenciesFileType.REQUIREMENTS_TXT) {
                    bufferedWriter.write(pipPath + Constants.WHITESPACE + INSTALL + Constants.WHITESPACE + R_PARAMETER +
                            Constants.WHITESPACE + requirementsTxtPath + Constants.WHITESPACE + F + Constants.WHITESPACE + this.tempDirPackages);
                } else if (this.dependencyFileType == DependenciesFileType.SETUP_PY) {
                    bufferedWriter.write(pipPath + Constants.WHITESPACE + INSTALL + Constants.WHITESPACE +
                            Constants.WHITESPACE + requirementsTxtPath + Constants.WHITESPACE + F + Constants.WHITESPACE + this.tempDirPackages);
                }
                bufferedWriter.newLine();
                bufferedWriter.write(pipPath + Constants.WHITESPACE + INSTALL + Constants.WHITESPACE + PIPDEPTREE);
                bufferedWriter.newLine();
                bufferedWriter.write(PIPDEPTREE + Constants.WHITESPACE + JSON_TREE + Constants.WHITESPACE + ARROW + Constants.WHITESPACE + this.tempDirVirtualenv + HIERARCHY_TREE_TXT);
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
    private void runPipEnvInstallCommand(String[] args){

        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command(args);
            builder.directory(new File(this.topLevelFolder));
            Process process = builder.start();
            StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
            Executors.newSingleThreadExecutor().submit(streamGobbler);
            //for debug mode, to check errors
            BufferedReader inputStreamPipEnvInstall = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorStreamPipEnvInstall = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String cmdOutput;
            while ((cmdOutput = inputStreamPipEnvInstall.readLine()) != null) {
                logger.debug(cmdOutput);
            }
            while ((cmdOutput = errorStreamPipEnvInstall.readLine()) != null) {
                logger.debug(cmdOutput);
            }
            int exitCode = 0;
            exitCode = process.waitFor();
            logger.debug("Exit Code: {}", exitCode);
        } catch (IOException e){
            logger.warn("IOException: {}", e.getMessage());
            logger.debug("IOException: {}", e.getStackTrace());
        } catch (InterruptedException e) {
            logger.warn("Interrupted Exception: {}", e.getMessage());
            logger.debug("Interrupted Exception: {}", e.getStackTrace());
        }
    }
    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }
        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
        }
    }
}

