package org.whitesource.agent.dependency.resolver.gradle;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.maven.MavenTreeDependencyCollector;
import org.whitesource.agent.utils.FilesScanner;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class represents parser for Maven output lines
 *
 * @author erez.huberman
 */
public class GradleLinesParser extends MavenTreeDependencyCollector {
    protected static final String ARROW = " -> ";
    protected static final String PROJECT = "project :";

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(GradleLinesParser.class);
    private static final String TMP_JAVA_FILE = "tmp.java";
    private static final String MAIN = "main";
    private static final String JAVA = "java";
    private static final String JAVA_EXTENSION = ".java";
    private static final String AAR_EXTENSION = ".aar";
    private static final String EXE_EXTENSION = ".exe";
    private static final String PLUS = "+---";
    private static final String SLASH = "\\---";
    private static final int INDENTETION_SPACE = 5;
    private static final String JAR_EXTENSION = Constants.DOT + Constants.JAR;
    private static final String ASTERIX = "(*)";
    public static final String[] GRADLE_PARSER_EXCLUDES = {"-sources"};

    private String fileSeparator;
    private String dotGradlePath;
    private String rootDirectory;
    private String directoryName;
    private String prevRootDirectory;
    private boolean runAssembleCommand;
    private boolean dependenciesDownloadAttemptPerformed;
    private GradleCli gradleCli;
    private String javaDirPath;
    private String mainDirPath;
    private String srcDirPath;
    private String gradleLocalRepositoryPath;
    private boolean removeJavaDir;
    private boolean removeMainDir;
    private boolean removeSrcDir;
    private boolean removeJavaFile;
    private boolean mavenFound = true;

    GradleLinesParser(boolean runAssembleCommand, GradleCli gradleCli, String gradleLocalRepositoryPath) {
        // send maven.runPreStep default value "false", irrelevant for gradle dependency resolution. (WSE-860)
        super(null, true, false, false);
        this.runAssembleCommand = runAssembleCommand;
        this.gradleLocalRepositoryPath = gradleLocalRepositoryPath;
        // using the same 'gradleCli' object as the GradleDependencyResolver for if the value of 'preferredEnvironment' changes,
        // it should change for both GradleLInesParser and GradleDependencyResolver
        this.gradleCli = gradleCli;
        fileSeparator = System.getProperty(Constants.FILE_SEPARATOR);
        srcDirPath = fileSeparator + Constants.SRC;
        mainDirPath = srcDirPath + fileSeparator + MAIN;
        javaDirPath = mainDirPath + fileSeparator + JAVA;
    }

    public List<DependencyInfo> parseLines(List<String> lines, String rootDirectory, String directoryName, String[] ignoredScopes, String bomFile) {
        if (StringUtils.isBlank(dotGradlePath)) {
            this.dotGradlePath = getDotGradleFolderPath();
        }
        if (this.dotGradlePath == null) {
            return new ArrayList<>();
        }
        this.rootDirectory = rootDirectory;
        this.directoryName = directoryName;
        logger.info("Start parsing gradle dependencies of: {}", rootDirectory + directoryName);

        //parse dependencies
        //check if to ignore scopes and parse lines of gradle dependencies to map of scopes
        if (ignoredScopes.length != 0) {
            lines = ignoreScopesOfGradleDependencies(ignoredScopes, lines);
        }
        List<String> projectsLines = lines.stream()
                .filter(line -> (line.contains(PLUS) || line.contains(SLASH) || line.contains(Constants.PIPE)) && !line.contains(ASTERIX))
                .collect(Collectors.toList());
        List<DependencyInfo> dependenciesList = new ArrayList<>();
        Stack<DependencyInfo> parentDependencies = new Stack<>();
        List<String> sha1s = new ArrayList<>();
        int prevLineIndentation = 0;
        boolean duplicateDependency = false;
        boolean insideProject = false;
        for (String line : projectsLines) {
            try {
                if (line.indexOf(Constants.COLON) == -1 || line.contains(PROJECT)) {
                    if (line.contains(PROJECT)) {
                        /*
                        * there may be such scenarios -
                         \--- project :tapestry-ioc
                                +--- project :tapestry-func
                                +--- project :tapestry5-annotations
                                +--- project :plastic
                                |    \--- org.slf4j:slf4j-api:1.7.25
                                +--- project :beanmodel
                                |    +--- org.antlr:antlr:3.5.2
                                |    |    +--- org.antlr:antlr-runtime:3.5.2
                                |    |    \--- org.antlr:ST4:4.0.8
                                |    |         \--- org.antlr:antlr-runtime:3.5.2
                          in such case - ignore the lines starting with 'project' but collect their transitive dependencies
                          and add them to the root of the dependencies' tree root
                        * */
                        prevLineIndentation = 0;
                        insideProject = true;
                    }
                    continue;
                }

                String[] strings = line.split(Constants.COLON);
                String groupId = strings[0];
                int lastSpace = groupId.lastIndexOf(Constants.WHITESPACE);
                groupId = groupId.substring(lastSpace + 1);
                String artifactId, version;
                if (strings.length == 2) {
                    artifactId = strings[1].split(ARROW)[0];
                    version = strings[1].split(ARROW)[1];
                } else {
                    artifactId = strings[1];
                    version = strings[2];
                    if (version.contains(Constants.WHITESPACE)) {
                        if (version.contains(ARROW)) {
                            version = version.split(ARROW)[1];
                        } else {
                            version = version.split(Constants.WHITESPACE)[0];
                        }
                    }
                }

                // Create dependencyInfo & calculate SHA1
                DependencyInfo currentDependency = new DependencyInfo(groupId, artifactId, version);
                DependencyFile dependencyFile = getDependencySha1(currentDependency);
                if (dependencyFile != null && !dependencyFile.getSha1().equals(Constants.EMPTY_STRING)) {
                    if (sha1s.contains(dependencyFile.getSha1()))
                        continue;
                    sha1s.add(dependencyFile.getSha1());
                    currentDependency.setSha1(dependencyFile.getSha1());
                    currentDependency.setSystemPath(dependencyFile.getFilePath());
                    currentDependency.setFilename(dependencyFile.getFileName());
                    currentDependency.setDependencyFile(bomFile);
                    String extension = FilesUtils.getFileExtension(dependencyFile.getFilePath());
                    currentDependency.setType(extension);
                }
                currentDependency.setDependencyType(DependencyType.GRADLE);

                if (dependenciesList.contains(currentDependency)) {
                    duplicateDependency = true;
                    continue;
                }
                // In case the dependency is transitive/child dependency
                if ((line.startsWith(Constants.WHITESPACE) || line.startsWith(Constants.PIPE)) && !insideProject) {
                    if (duplicateDependency || parentDependencies.isEmpty()) {
                        continue;
                    }
                    // Check if 2 dependencies are siblings (under the hierarchy level)
                    if (lastSpace == prevLineIndentation) {
                        parentDependencies.pop();

                    } else if (lastSpace < prevLineIndentation) {
                        // Find father dependency of current node
                    /*+--- org.webjars.npm:isurl:1.0.0
                      |    +--- org.webjars.npm:has-to-string-tag-x:[1.2.0,2) -> 1.4.1
                      |    |    \--- org.webjars.npm:has-symbol-support-x:[1.4.1,2) -> 1.4.1
                      |    \--- org.webjars.npm:is-object:[1.0.1,2) -> 1.0.1
                    */
                        while (prevLineIndentation > lastSpace - INDENTETION_SPACE && !parentDependencies.isEmpty()) {
                            parentDependencies.pop();
                            prevLineIndentation -= INDENTETION_SPACE;
                        }
                    }

                    if (!parentDependencies.isEmpty()) {
                        parentDependencies.peek().getChildren().add(currentDependency);
                    } else {
                        // if - for some reason - this is a transitive dependency but the parent-dependencies stack is empty,
                        // add this dependency to the root of the tree
                        dependenciesList.add(currentDependency);
                    }
                    parentDependencies.push(currentDependency);
                } else {
                    duplicateDependency = false;
                    insideProject = false;
                    dependenciesList.add(currentDependency);
                    parentDependencies.clear();
                    parentDependencies.push(currentDependency);
                }
                prevLineIndentation = lastSpace;
            } catch (Exception e) {
                logger.warn("Couldn't parse line {}, error: {} - {}", line, e.getClass().toString(), e.getMessage());
                logger.debug("Exception: {}", e.getStackTrace());
            }
        }

        return dependenciesList;
    }

    private List<String> ignoreScopesOfGradleDependencies(String[] ignoredScopes, List<String> lines) {
        String scope = Constants.EMPTY_STRING;
        Map<String, String> gradleScopes = new HashMap<>();
        for (String line : lines) {
            if (Character.isLetter(line.charAt(0))) {
                int indexOfWhiteSpace = line.indexOf(' ');
                // in case line is a single word take it.. else take the first word before whitespace
                if (indexOfWhiteSpace == -1) {
                    scope = line;
                } else {
                    scope = line.substring(0, indexOfWhiteSpace);
                }
                gradleScopes.put(scope, "");
            } else {
                if (gradleScopes.containsKey(scope)) {
                    String strConcatinator = gradleScopes.get(scope);
                    strConcatinator = strConcatinator + line + "\n";
                    gradleScopes.put(scope, strConcatinator);
                }

            }
        }
        // remove ignoredScopes from scopes if exists
        for (String ignoredScope : ignoredScopes) {
            gradleScopes.remove(ignoredScope);
        }
        return Arrays.asList(gradleScopes.values().toString().split("\n"));
    }

    private String getDotGradleFolderPath() {
        String currentUsersHomeDir = System.getProperty(Constants.USER_HOME);
        File dotGradle = Paths.get(currentUsersHomeDir, ".gradle", "caches", "modules-2", "files-2.1").toFile();

        if (dotGradle.exists()) {
            return dotGradle.getAbsolutePath();
        }
        logger.error("Could not get .gradle path, dependencies information will not be send to WhiteSource server.");
        return null;
    }

    private DependencyFile getDependencySha1(DependencyInfo dependencyInfo) {
        DependencyFile dependencyFile = getSha1FromGradleCache(dependencyInfo);
        if (dependencyFile == null) {
            // if dependency not found in .gradle cache - looking for it in .m2 cache
            dependencyFile = getSha1FromM2(dependencyInfo);
            if (dependencyFile == null || dependencyFile.getSha1().equals(Constants.EMPTY_STRING)) {
                // if dependency not found in .m2 cache - running 'gradle assemble' command which should download the dependency to .grade cache
                // making sure the download attempt is performed only once for a directory, otherwise there might be an infinite loop
                if (!rootDirectory.concat(directoryName).equals(prevRootDirectory)) {
                    dependenciesDownloadAttemptPerformed = false;
                }
                if (!dependenciesDownloadAttemptPerformed && downloadDependencies()) {
                    dependencyFile = getDependencySha1(dependencyInfo);
                } else {
                    dependencyFile = getSha1FromLocalRepo(dependencyInfo);
                    if (dependencyFile == null) {
                        logger.error("Couldn't find sha1 for " + dependencyInfo.getGroupId() + Constants.DOT +
                                dependencyInfo.getArtifactId() + Constants.DOT + dependencyInfo.getVersion());
                    }
                }
            }
        }
        return dependencyFile;
    }

    // get sha1 form local repository
    private DependencyFile getSha1FromLocalRepo(DependencyInfo dependencyInfo) {
        File localRepo = new File(gradleLocalRepositoryPath);
        DependencyFile dependencyFile = null;
        if (localRepo.exists() && localRepo.isDirectory()) {
            String artifactId = dependencyInfo.getArtifactId();
            String version = dependencyInfo.getVersion();
            String dependencyName = artifactId + Constants.DASH + version;
            logger.debug("Looking for " + dependencyName + " in {}", localRepo.getPath());
            dependencyFile = findDependencySha1(dependencyName, gradleLocalRepositoryPath);
        } else {
            logger.warn("Could not find path {}", localRepo.getPath());
        }
        return dependencyFile;
    }

    private DependencyFile getSha1FromGradleCache(DependencyInfo dependencyInfo) {
        String groupId = dependencyInfo.getGroupId();
        String artifactId = dependencyInfo.getArtifactId();
        String version = dependencyInfo.getVersion();
        logger.debug("looking for " + groupId + "." + artifactId + "." + version + " in .gradle cache");
        String dependencyName = artifactId + Constants.DASH + version;
        DependencyFile dependencyFile = null;
        // gradle file path includes the sha1
        if (dotGradlePath != null) {
            String pathToDependency = dotGradlePath.concat(fileSeparator + groupId + fileSeparator + artifactId + fileSeparator + version);
            File dependencyFolder = new File(pathToDependency);
            // parsing gradle file path, get file hash from its path. the dependency folder version contains
            // 2 folders one for pom and another for the jar. Look for the one with the jar in order to get the sha1
            // .gradle\caches\modules-2\files-2.1\junit\junit\4.12\2973d150c0dc1fefe998f834810d68f278ea58ec
            if (dependencyFolder.isDirectory()) {
                dependencyFile = findDependencySha1(dependencyName, pathToDependency);
            }
        }
        if (dependencyFile == null) {
            logger.debug("Couldn't find sha1 for " + groupId + Constants.DOT + artifactId + Constants.DOT + version + " inside .gradle cache.");
        }
        return dependencyFile;
    }

    private DependencyFile getSha1FromM2(DependencyInfo dependencyInfo) {
        if (!mavenFound)
            return null;
        String groupId = dependencyInfo.getGroupId();
        String artifactId = dependencyInfo.getArtifactId();
        String version = dependencyInfo.getVersion();
        logger.debug("looking for " + groupId + "." + artifactId + "." + version + " in .m2 cache");
        DependencyFile dependencyFile = null;
        if (StringUtils.isBlank(M2Path)) {
            this.M2Path = getMavenM2Path(Constants.DOT);
            if (M2Path == null) {
                logger.debug("Couldn't find .m2 path - maven is not installed");
                mavenFound = false;
                return null;
            }
        }

        String pathToDependency = M2Path.concat(fileSeparator + String.join(fileSeparator, groupId.split("\\.")) +
                fileSeparator + artifactId + fileSeparator + version
                + fileSeparator + artifactId + Constants.DASH + version + Constants.JAR_EXTENSION);
        File file = new File(pathToDependency);
        if (file.isFile()) {
            String sha1 = getSha1(pathToDependency);
            dependencyFile = new DependencyFile(sha1, file);
            if (sha1.equals(Constants.EMPTY_STRING)) {
                logger.debug("Couldn't calculate sha1 for " + groupId + Constants.DOT + artifactId + Constants.DOT + version + ".  ");
            }
        } else {
            logger.debug("Couldn't find sha1 for " + groupId + Constants.DOT + artifactId + Constants.DOT + version + " inside .m2 cache.");
        }
        return dependencyFile;
    }

    private boolean downloadDependencies() {
        dependenciesDownloadAttemptPerformed = true;
        prevRootDirectory = rootDirectory.concat(directoryName);
        if (runAssembleCommand) {
            try {
                logger.info("running 'gradle assemble' command");
                long creationTime = new Date().getTime(); // will be used later for removing temp files/folders
                validateJavaFileExistence();
                String[] gradleCommandParams = gradleCli.getGradleCommandParams(GradleMvnCommand.ASSEMBLE);
                List<String> lines = gradleCli.runCmd(rootDirectory, gradleCommandParams);
                //removeTempJavaFolder();
                String errors = FilesUtils.removeTempFiles(rootDirectory, creationTime);
                if (!errors.isEmpty()) {
                    logger.error(errors);
                }
                if (!lines.isEmpty()) {
                    for (String line : lines) {
                        if (line.contains("BUILD SUCCESSFUL")) {
                            return true;
                        }
                    }
                }
            } catch (IOException e) {
                logger.debug("Failed running 'gradle assemble' command, got exception: " + e.getMessage());
            }
        } else {
            logger.debug("Can't run 'gradle assemble' to download missing dependencies.  Change 'gradle.runAssembleCommand' in the configuration file to 'true'");
        }
        logger.error("Failed running 'gradle assemble' command");
        return false;
    }

    private void validateJavaFileExistence() throws IOException {
        // the 'gradle assemble' command, existence of a java file (even empty) inside 'src/main/java' is required.
        // therefore, verifying the file and the path exist - if not creating it, and after running the assemble command removing the item that was added
        String javaDirPath = rootDirectory + directoryName + this.javaDirPath;
        File javaDir = new File(javaDirPath);
        String srcDirPath = rootDirectory + directoryName + this.srcDirPath;
        File srcDir = new File(srcDirPath);
        removeSrcDir = false;
        if (!srcDir.isDirectory()) { // src folder doesn't exist - create the whole tree
            FileUtils.forceMkdir(javaDir);
            logger.debug("no 'src' folder, created temp " + javaDirPath);
            removeSrcDir = true;
        } else {
            String mainDirPath = rootDirectory + directoryName + this.mainDirPath;
            File mainDir = new File(mainDirPath);
            removeMainDir = false;
            if (!mainDir.isDirectory()) { // main folder doesn't exist - create it with its sub-folder
                FileUtils.forceMkdir(javaDir);
                logger.debug("no 'src/main' folder, created temp " + javaDirPath);
                removeMainDir = true;
            } else {
                removeJavaDir = false;
                if (!javaDir.isDirectory()) { // java folder doesn't exist - create it
                    FileUtils.forceMkdir(javaDir);
                    logger.debug("no 'src/main/java' folder, created temp " + javaDirPath);
                    removeJavaDir = true;
                }
            }
        }
        removeJavaFile = false;
        if (!javaFileExists(rootDirectory + directoryName + this.javaDirPath)) { // the java folder doesn't have any java file inside it - creating a temp file
            File javaFile = new File(javaDirPath + fileSeparator + TMP_JAVA_FILE);
            removeJavaFile = javaFile.createNewFile();
            logger.debug("no java file, created temp " + javaFile.getPath());
        }
    }

    private boolean javaFileExists(String directoryName) {
        File directory = new File(directoryName);
        File[] fList = directory.listFiles();
        if (fList != null) {
            for (File file : fList) {
                if (file.isFile()) {
                    if (file.getName().endsWith(JAVA_EXTENSION))
                        return true;
                } else if (file.isDirectory()) {
                    return javaFileExists(file.getAbsolutePath());
                }
            }
        }
        return false;
    }

    private DependencyFile findDependencySha1(String dependencyName, String pathToDependency) {
        FilesScanner filesScanner = new FilesScanner();
        DependencyFile dependencyFile = null;
        String[] parserIncludes = new String[]{Constants.GLOB_PATTERN_PREFIX + dependencyName + JAR_EXTENSION, Constants.GLOB_PATTERN_PREFIX + dependencyName + EXE_EXTENSION,
                Constants.GLOB_PATTERN_PREFIX + dependencyName + AAR_EXTENSION};
        String[] directoryContent = filesScanner.getDirectoryContent(pathToDependency, parserIncludes, GRADLE_PARSER_EXCLUDES,
                true, false, false);
        if (directoryContent.length != 0) {
            for (String dependencyFileName : directoryContent) {
                if (dependencyFileName.contains(dependencyName)) {
                    String sha1 = getSha1(pathToDependency + File.separator + dependencyFileName);
                    // try to find the first file match
                    if (StringUtils.isNotBlank(sha1)) {
                        dependencyFile = new DependencyFile(sha1, new File(pathToDependency + File.separator + dependencyFileName));
                        break;
                    } else {
                        logger.debug("Couldn't find sha1 for " + pathToDependency + File.separator + dependencyFileName + " inside .gradle cache.");
                    }
                }
            }
        } else {
            logger.debug("Could not find the dependency {} in {}", dependencyName, pathToDependency);
        }
        return dependencyFile;
    }

    // removing only the folders/ file that were created
    private void removeTempJavaFolder() throws IOException {
        if (removeJavaDir) {
            FileUtils.forceDelete(new File(rootDirectory + directoryName + this.javaDirPath));
        } else if (removeMainDir) {
            FileUtils.forceDelete(new File(rootDirectory + directoryName + this.mainDirPath));
        } else if (removeSrcDir) {
            FileUtils.forceDelete(new File(rootDirectory + directoryName + this.srcDirPath));
        } else if (removeJavaFile) {
            FileUtils.forceDelete(new File(rootDirectory + directoryName + javaDirPath + fileSeparator + TMP_JAVA_FILE));
        }
    }

    private class DependencyFile {
        private String sha1;
        private String filePath;
        private String fileName;

        public DependencyFile(String sha1, File file) {
            this.sha1 = sha1;
            this.filePath = file.getPath();
            this.fileName = file.getName();
        }

        public String getSha1() {
            return sha1;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getFileName() {
            return fileName;
        }
    }
}
