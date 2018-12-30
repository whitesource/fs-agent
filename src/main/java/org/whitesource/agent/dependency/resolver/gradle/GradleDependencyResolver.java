package org.whitesource.agent.dependency.resolver.gradle;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.TempFolders;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.fs.Main;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GradleDependencyResolver extends AbstractDependencyResolver {

    /* --- Static members --- */

    private static final List<String> GRADLE_SCRIPT_EXTENSION = Arrays.asList(".gradle", ".groovy", ".java", ".jar", ".war", ".ear", ".car", ".class");

    private static final String JAR_EXTENSION = Constants.DOT + Constants.JAR;
    private static final String PROJECT = "--- Project";
    public static final String COPY_DEPENDENCIES_TASK_TXT = "copyDependenciesTask.txt";
    private static final String DEPENDENCIES = "dependencies";
    private static final String CURLY_BRACKETS_OPEN = "{";
    private static final String CURLY_BRACKTES_CLOSE = "}";
    private static final String TASK_COPY_DEPENDENCIES_HEADER = "task copyDependencies(type: Copy) {";
    private static final String TASK_COPY_DEPENDENCIES_FOOTER = "    into \"lib\"";

    /* --- Private Members --- */

    private String[] ignoredScopes;
    private GradleLinesParser gradleLinesParser;
    private GradleCli gradleCli;
    private ArrayList<String> topLevelFoldersNames;
    private boolean ignoreSourceCode;
    private boolean gradleAggregateModules;
    private boolean gradleRunPreStep;
    private HashMap<String, List<String>> dependencyTrees;


    private final Logger logger = LoggerFactory.getLogger(GradleDependencyResolver.class);

    /* --- Constructors --- */

    public GradleDependencyResolver(boolean runAssembleCommand, boolean ignoreSourceCode, boolean gradleAggregateModules, String gradlePreferredEnvironment, String[] gradleIgnoredScopes,
                                    String gradleLocalRepositoryPath, boolean gradleRunPreStep) {
        super();
        gradleCli = new GradleCli(gradlePreferredEnvironment);
        gradleLinesParser = new GradleLinesParser(runAssembleCommand, gradleCli, gradleLocalRepositoryPath);
        this.ignoredScopes = gradleIgnoredScopes;
        topLevelFoldersNames = new ArrayList<>();
        this.ignoreSourceCode = ignoreSourceCode;
        this.gradleAggregateModules = gradleAggregateModules;
        this.gradleRunPreStep = gradleRunPreStep;
        this.dependencyTrees = new HashMap<>();
    }

    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        logger.debug("gradleAggregateModules={}",gradleAggregateModules);
        // In order to use the gradle wrapper, we define the top folder that contains the wrapper
        this.gradleCli.setTopLevelFolderGradlew(topLevelFolder);
        // each bom-file ( = build.gradle) represents a module - identify its folder and scan it using 'gradle dependencies'
        Map<AgentProjectInfo, Path> projectInfoPathMap = new HashMap<>();
        Collection<String> excludes = new HashSet<>();

        //        // Get the list of projects as paths
        //        List<String> projectsList = null;
        //        if (bomFiles.size() > 1) {
        //            projectsList = collectProjects(topLevelFolder);
        //        }
        //        if (projectsList == null) {
        //            logger.warn("Command \"gradle projects\" did not return a list of projects");
        //        }
        if (gradleRunPreStep) {
            downloadMissingDependencies(projectFolder);
        }

        for (String bomFile : bomFiles) {
            String bomFileFolder = new File(bomFile).getParent();
            File bomFolder = new File(new File(bomFile).getParent());
            String moduleName = bomFolder.getName();
            //            String moduleRelativeName = Constants.EMPTY_STRING;
            //            try {
            //                String canonicalPath = bomFolder.getCanonicalPath();
            //                // Relative name by replacing the root folder with "." - will look something like .\abc\def
            //                moduleRelativeName = Constants.DOT + canonicalPath.replaceFirst(Pattern.quote(topLevelFolder), Constants.EMPTY_STRING);
            //            } catch (Exception e) {
            //                logger.debug("Error getting path - {} ", e.getMessage());
            //            }
            //            // making sure the module's folder was listed by "gradle projects" command
            //            if (!moduleRelativeName.isEmpty() && projectsList != null && !projectsList.contains(moduleRelativeName)) {
            //                logger.debug("Ignoring project at {} - because it was not listed by \"gradle projects\" command", moduleRelativeName);
            //                continue;
            //            }
            List<String> lines = getDependenciesTree(bomFileFolder, moduleName);
            if (lines != null) {
                List<DependencyInfo> dependencies = collectDependencies(lines, bomFileFolder, bomFileFolder.equals(topLevelFolder), bomFile);
                if (dependencies.size() > 0) {
                    AgentProjectInfo agentProjectInfo = new AgentProjectInfo();
                    agentProjectInfo.getDependencies().addAll(dependencies);
                    if (!gradleAggregateModules) {
                        Coordinates coordinates = new Coordinates();
                        coordinates.setArtifactId(moduleName);
                        agentProjectInfo.setCoordinates(coordinates);
                    }
                    projectInfoPathMap.put(agentProjectInfo, bomFolder.toPath());
                    if (ignoreSourceCode) {
                        excludes.addAll(normalizeLocalPath(projectFolder, topLevelFolder, extensionPattern(GRADLE_SCRIPT_EXTENSION), null));
                    }
                }
            }
        }
        topLevelFoldersNames.add(topLevelFolder.substring(topLevelFolder.lastIndexOf(fileSeparator) + 1));
        excludes.addAll(getExcludes());
        ResolutionResult resolutionResult;
        if (!gradleAggregateModules) {
            resolutionResult = new ResolutionResult(projectInfoPathMap, excludes, getDependencyType(), topLevelFolder);
        } else {
            resolutionResult = new ResolutionResult(projectInfoPathMap.keySet().stream()
                    .flatMap(project -> project.getDependencies().stream()).collect(Collectors.toList()), excludes, getDependencyType(), topLevelFolder);
        }
        logger.debug("total projects = {}",resolutionResult.getResolvedProjects().size());
        return resolutionResult;
    }

    @Override
    protected Collection<String> getExcludes() {
        Set<String> excludes = new HashSet<>();
        for (String topLeverFolderName : topLevelFoldersNames) {
            excludes.add(GLOB_PATTERN + topLeverFolderName + Constants.JAR_EXTENSION);
        }
        return excludes;
    }

    @Override
    public Collection<String> getSourceFileExtensions() {
        return GRADLE_SCRIPT_EXTENSION;
    }

    @Override
    protected DependencyType getDependencyType() {
        return DependencyType.GRADLE;
    }

    @Override
    protected String getDependencyTypeName() {
        return DependencyType.GRADLE.name();
    }

    @Override
    protected String[] getBomPattern() {
        return new String[]{Constants.GLOB_PATTERN_PREFIX + Constants.BUILD_GRADLE};
    }

    @Override
    public Collection<String> getManifestFiles(){
        return Arrays.asList(Constants.BUILD_GRADLE);
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return null;
    }

    /* --- Private methods --- */

    private List<String> getDependenciesTree(String directory, String directoryName) {
        List<String> lines;
        if (dependencyTrees.get(directoryName) == null) {
            String[] gradleCommandParams = gradleCli.getGradleCommandParams(GradleMvnCommand.DEPENDENCIES);
            lines = gradleCli.runGradleCmd(directory, gradleCommandParams, true);
            dependencyTrees.put(directoryName, lines);
        } else {
            lines = dependencyTrees.get(directoryName);
        }
        return lines;
    }

    private List<DependencyInfo> collectDependencies(List<String> lines, String directory, boolean isParent, String bomFile) {
        List<DependencyInfo> dependencyInfos = new ArrayList<>();
        String directoryName = Constants.EMPTY_STRING;
        if (!isParent) {
            // get the name of the directory
            String[] directoryPath = directory.split(Pattern.quote(fileSeparator));
            directoryName = directoryPath[directoryPath.length - 1];
        }
        // get gradle dependencies, if the command runs successfully parse the dependencies
        directoryName = fileSeparator.concat(directoryName);
        dependencyInfos.addAll(gradleLinesParser.parseLines(lines, directory, directoryName, ignoredScopes, bomFile));
        return dependencyInfos;
    }

    private List<String> collectProjects(String rootDirectory) {
        List<String> projectsList = gradleCli.runGradleCmd(rootDirectory, gradleCli.getGradleCommandParams(GradleMvnCommand.PROJECTS), true);
        List<String> resultProjectsList = null;
        if (projectsList != null) {
            resultProjectsList = new ArrayList<>();
            for (String line : projectsList) {
                if (line.contains(PROJECT)) {
                    // Relevant lines look like:
                    //  |    +--- Project ':nes:t4' - optional description for project
                    //  |    \--- Project ':nes:t5' - optional description for project
                    //  +--- Project ':template-server3'
                    // Split the line
                    String[] lineParts = line.split(PROJECT);
                    if (lineParts.length == 2) {
                        String partWithNameAndDescription = lineParts[1].trim();
                        String projectName;
                        // No description at the end of line
                        if (partWithNameAndDescription.endsWith(Constants.APOSTROPHE)) {
                            projectName = partWithNameAndDescription.trim().replaceAll(Constants.APOSTROPHE, Constants.EMPTY_STRING);
                        } else {
                            String[] projectAndDescription = partWithNameAndDescription.split(Constants.APOSTROPHE);
                            projectName = projectAndDescription[1];
                        }
                        // Convert the project name to a path name
                        // Example: :abc:def --> .\abc\def
                        String projectNameAsPath = Constants.DOT + projectName;
                        projectNameAsPath = projectNameAsPath.replaceAll(Constants.COLON, Matcher.quoteReplacement(File.separator));
                        resultProjectsList.add(projectNameAsPath);
                    }
                }
            }
        }
        return resultProjectsList;
    }

    // copy all the bom files (build.gradle) to temp folder and run the command "gradle copyDependencies"
    private void downloadMissingDependencies(String projectFolder) {
        logger.debug("running pre-steps on folder {}", projectFolder);
        String tempFolder = new FilesUtils().createTmpFolder(false, TempFolders.UNIQUE_GRADLE_TEMP_FOLDER);
        File buildGradleTempDirectory = new File(tempFolder);
        if (copyProjectFolder(projectFolder, buildGradleTempDirectory)) {
            try {
                Stream<Path> pathStream = Files.walk(Paths.get(buildGradleTempDirectory.getPath()), Integer.MAX_VALUE).filter(file -> file.getFileName().toString().equals(Constants.BUILD_GRADLE));
                pathStream.forEach(path -> {
                    File buildGradleTmp = new File(path.toString());
                    if (buildGradleTmp.exists()) {
                        if (appendTaskToBomFile(buildGradleTmp)) {
                            runPreStepCommand(buildGradleTmp);
                            removeTaskFromBomFile(buildGradleTmp);
                        }
                    } else {
                        logger.warn("Could not find the path {}", buildGradleTmp.getPath());
                    }
                });
            } catch (IOException e) {
                logger.warn("Couldn't list all 'build.gradle' files, error: {}", e.getMessage());
                logger.debug("Error: {}", e.getStackTrace());
            } finally {
                new TempFolders().deleteTempFoldersHelper(Paths.get(System.getProperty("java.io.tmpdir"), TempFolders.UNIQUE_GRADLE_TEMP_FOLDER).toString());
            }
        }
    }

    // copy project to local temp directory
    private boolean copyProjectFolder(String projectFolder, File buildGradleTempDirectory) {
        try {
            FileUtils.copyDirectory(new File(projectFolder), buildGradleTempDirectory);
        } catch (IOException e) {
            logger.error("Could not copy the folder {} to {} , the cause {}", projectFolder, buildGradleTempDirectory.getPath(), e.getMessage());
            return false;
        }
        logger.debug("copied folder {} to temp folder successfully", projectFolder);
        return true;
    }

    // append new task to bom file
    private boolean appendTaskToBomFile(File buildGradleTmp) {
        FileReader fileReader;
        BufferedReader bufferedReader = null;
        InputStream inputStream = null;
        boolean hasDependencies = false;
        try {
            // appending the task only if the build.gradle file has 'dependencies {' node (only at the beginning of the line)
            // otherwise, later when the task is ran it'll fail
            fileReader = new FileReader(buildGradleTmp);
            bufferedReader = new BufferedReader(fileReader);
            String currLine;
            while ((currLine = bufferedReader.readLine()) != null) {
                if (currLine.indexOf(DEPENDENCIES + Constants.WHITESPACE + CURLY_BRACKETS_OPEN) == 0 || currLine.indexOf(DEPENDENCIES + CURLY_BRACKETS_OPEN) == 0) {
                    hasDependencies = true;
                    break;
                }
            }
            if (hasDependencies) {
                byte[] bytes;
                List<String> lines = getDependenciesTree(buildGradleTmp.getParent(), buildGradleTmp.getParentFile().getName());
                if (lines != null) {
                    List<String> scopes = getScopes(lines);
                    String copyDependenciesTask = Constants.NEW_LINE + TASK_COPY_DEPENDENCIES_HEADER + Constants.NEW_LINE;
                    for (String scope : scopes) {
                        copyDependenciesTask = copyDependenciesTask.concat("   from configurations." + scope + Constants.NEW_LINE);
                    }
                    copyDependenciesTask = copyDependenciesTask.concat(TASK_COPY_DEPENDENCIES_FOOTER + Constants.NEW_LINE + CURLY_BRACKTES_CLOSE);
                    bytes = copyDependenciesTask.getBytes();
                } else {
                    ClassLoader classLoader = Main.class.getClassLoader();
                    inputStream = classLoader.getResourceAsStream(COPY_DEPENDENCIES_TASK_TXT);
                    bytes = IOUtils.toByteArray(inputStream);
                }
                if (bytes.length > 0) {
                    Files.write(Paths.get(buildGradleTmp.getPath()), bytes, StandardOpenOption.APPEND);
                } else if (lines == null) {
                    logger.warn("Could not read {}", COPY_DEPENDENCIES_TASK_TXT);
                } else {
                    logger.warn("Could not read dependencies' tree");
                }
            }
        } catch (IOException e) {
            logger.error("Could not write into the file {}, the cause {}", buildGradleTmp.getPath(), e.getMessage());
            hasDependencies = false;
        }
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        } catch (IOException e) {
            logger.error("Could close the file, cause", e.getMessage());
        }
        return hasDependencies;
    }

    private List<String> getScopes(List<String> lines) {
        List<String> scopes = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String currLine = lines.get(i);
            String prevLine = lines.get(i - 1);
            if ((currLine.startsWith(Constants.PLUS) || currLine.startsWith(Constants.BACK_SLASH)) &&
                    (!prevLine.startsWith(Constants.WHITESPACE) && !prevLine.startsWith(Constants.PIPE) && !prevLine.startsWith(Constants.PLUS) && !prevLine.startsWith(Constants.BACK_SLASH))) {
                String scope = prevLine.split(" - ")[0];
                scopes.add(scope);
            }
        }
        return scopes;
    }

    // run pre step command gradle copyDependencies
    private void runPreStepCommand(File bomFile) {
        String directory = bomFile.getParent();
        String[] gradleCommandParams = gradleCli.getGradleCommandParams(GradleMvnCommand.COPY_DEPENDENCIES);
        if (StringUtils.isNotEmpty(directory) && gradleCommandParams.length > 0) {
            gradleCli.runGradleCmd(directory, gradleCommandParams, true);
        } else {
            logger.warn("Could not run gradle command");
        }
    }

    // there are cases where modules are connected to each other, and in such cases some scopes inside the copy-dependencies task interfere with other modules;
    // therefore, after running the copy-dependencies task removing it from the build.gradle file
    private void removeTaskFromBomFile(File buildGradleTmp) {
        FileReader fileReader;
        BufferedReader bufferedReader = null;
        try {
            fileReader = new FileReader(buildGradleTmp);
            bufferedReader = new BufferedReader(fileReader);
            String currLine;
            String originalLines = "";
            while ((currLine = bufferedReader.readLine()) != null) {
                if (currLine.equals(TASK_COPY_DEPENDENCIES_HEADER)) {
                    break;
                } else {
                    originalLines = originalLines.concat(currLine + Constants.NEW_LINE);
                }
            }
            if (!originalLines.isEmpty()) {
                byte[] bytes = originalLines.getBytes();
                Files.write(Paths.get(buildGradleTmp.getPath()), bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            logger.warn("Couldn't remove 'copyDependencies' task from {}, error: {}", buildGradleTmp.getPath(), e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            } catch (IOException e) {
                logger.error("Could close the file, cause", e.getMessage());
            }
        }
    }
}