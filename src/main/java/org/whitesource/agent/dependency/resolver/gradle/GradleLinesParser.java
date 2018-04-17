package org.whitesource.agent.dependency.resolver.gradle;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.maven.MavenTreeDependencyCollector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class represents parser for Maven output lines
 *
 * @author erez.huberman
 */
public class GradleLinesParser extends MavenTreeDependencyCollector {

    /* --- Static members --- */
    private static final String TMP_JAVA_FILE = "tmp.java";
    private static final String SRC = "src";
    private static final String MAIN = "main";
    private static final String JAVA = "java";
    private static final String JAVA_EXTENSION = ".java";
    private static final String AAR_EXTENTION = ".aar";
    private final Logger logger = LoggerFactory.getLogger(GradleLinesParser.class);
    private static final String PLUS = "+---";
    private static final String SLASH = "\\---";
    private static final String SPACE = " ";
    private static final String PIPE = "|";
    private static final String USER_HOME = "user.home";
    private static final String COLON = ":";
    private static final int INDENTETION_SPACE = 5;
    private static final String EMPTY_STRING = "";
    private static final String FILE_SEPARATOR = "file.separator";
    private static final String JAR_EXTENSION = ".jar";
    private static final String ASTERIX = "(*)";

    private String fileSeparator;
    private String dotGradlePath;
    private String rootDirectory;
    private boolean runAssembleCommand;
    private boolean dependenciesDownloadAttemptPerformed;
    private GradleCli gradleCli;
    private String javaDirPath;
    private String mainDirPath;
    private String srcDirPath;
    private boolean removeJavaDir;
    private boolean removeMainDir;
    private boolean removeSrcDir;
    private boolean removeJavaFile;

    GradleLinesParser(boolean runAssembleCommand){
        super(null);
        this.runAssembleCommand = runAssembleCommand;
        gradleCli = new GradleCli();
        fileSeparator = System.getProperty(FILE_SEPARATOR);
        srcDirPath = fileSeparator + SRC;
        mainDirPath = srcDirPath + fileSeparator + MAIN;
        javaDirPath = mainDirPath + fileSeparator+ JAVA;
    }

    public List<DependencyInfo> parseLines(List<String> lines, String rootDirectory) {
        if (StringUtils.isBlank(dotGradlePath)){
            this.dotGradlePath = getDotGradleFolderPath();
        }
        if (this.dotGradlePath == null){
            return new ArrayList<>();
        }
        this.rootDirectory = rootDirectory;
        List<String> projectsLines = lines.stream()
                .filter(line->(line.contains(PLUS) || line.contains(SLASH) || line.contains(PIPE)) && !line.contains(ASTERIX))
                .collect(Collectors.toList());

        logger.info("Start parsing gradle dependencies");
        List<DependencyInfo> dependenciesList = new ArrayList<>();
        Stack<DependencyInfo> parentDependencies = new Stack<>();
        List<String> sha1s = new ArrayList<>();
        int prevLineIndentation = 0;
        boolean duplicateDependency = false;
        for (String line : projectsLines){
            if (line.indexOf(COLON) == -1){
                continue;
            }
            String[] strings = line.split(COLON);
            String groupId = strings[0];
            int lastSpace = groupId.lastIndexOf(SPACE);
            groupId = groupId.substring(lastSpace + 1);
            String artifactId = strings[1];
            String version = strings[2];
            if (version.contains(SPACE)){
                if (version.contains("->")){
                    version = version.split(SPACE)[version.split(SPACE).length-1];
                } else {
                    version = version.split(SPACE)[0];
                }
            }
            // Create dependencyInfo & calculate SHA1
            DependencyInfo currentDependency = new DependencyInfo(groupId, artifactId, version);
            String sha1 = getDependencySha1(currentDependency);
            if (sha1 == null || sha1.equals(EMPTY_STRING) || sha1s.contains(sha1))
                continue;
            sha1s.add(sha1);
            currentDependency.setSha1(sha1);
            if (dependenciesList.contains(currentDependency)){
                duplicateDependency = true;
                continue;
            }
            // In case the dependency is transitive/child dependency
            if (line.startsWith(SPACE) || line.startsWith(PIPE)){
                if (duplicateDependency || parentDependencies.isEmpty())
                    continue;
                //if (!parentDependencies.isEmpty()) {
                    // Check if 2 dependencies are siblings (under the hierarchy level)
                    if (lastSpace == prevLineIndentation){
                        parentDependencies.pop();

                    } else if (lastSpace < prevLineIndentation) {
                    // Find father dependency of current node
                    /*+--- org.webjars.npm:isurl:1.0.0
                      |    +--- org.webjars.npm:has-to-string-tag-x:[1.2.0,2) -> 1.4.1
                      |    |    \--- org.webjars.npm:has-symbol-support-x:[1.4.1,2) -> 1.4.1
                      |    \--- org.webjars.npm:is-object:[1.0.1,2) -> 1.0.1
                    */
                        while (prevLineIndentation > lastSpace - INDENTETION_SPACE){
                            parentDependencies.pop();
                            prevLineIndentation -= INDENTETION_SPACE;
                        }
                    }
                    parentDependencies.peek().getChildren().add(currentDependency);
                //}
                parentDependencies.push(currentDependency);
            } else {
                duplicateDependency = false;
                dependenciesList.add(currentDependency);
                parentDependencies.clear();
                parentDependencies.push(currentDependency);
            }
            prevLineIndentation = lastSpace;
        }

        return dependenciesList;
    }

    private String getDotGradleFolderPath() {
        String currentUsersHomeDir = System.getProperty(USER_HOME);
        File dotGradle = Paths.get(currentUsersHomeDir, ".gradle", "caches","modules-2","files-2.1").toFile();

        if (dotGradle.exists()) {
            return dotGradle.getAbsolutePath();
        }
        logger.error("Could not get .gradle path, dependencies information will not be send to WhiteSource server.");
        return  null;
    }

    private String getDependencySha1(DependencyInfo dependencyInfo){
        String sha1 = getSha1FromGradleCache(dependencyInfo);
        if (sha1 == null){
            // if dependency not found in .gradle cache - looking for it in .m2 cache
            sha1 = getSha1FromM2(dependencyInfo);
            if (sha1 == null || sha1.equals(EMPTY_STRING)){
                // if dependency not found in .m2 cache - running 'gradel assemble' command which should download the dependency to .grade cache
                // making sure the download attempt is performed only once, otherwise there might be an infinite loop
                if (!dependenciesDownloadAttemptPerformed && downloadDependencies()){
                    sha1 = getDependencySha1(dependencyInfo);
                } else {
                    logger.error("Couldn't find sha1 for " + dependencyInfo.getGroupId() + "." + dependencyInfo.getArtifactId() + "." + dependencyInfo.getVersion());
                }
            }
        }
        return sha1;
    }

    private String getSha1FromGradleCache(DependencyInfo dependencyInfo){
        String groupId = dependencyInfo.getGroupId();
        String artifactId = dependencyInfo.getArtifactId();
        String version = dependencyInfo.getVersion();
        logger.debug("looking for " + groupId + "." + artifactId + "." + version + " in .gradle cache");
        String sha1 = null;
        // gradle file path includes the sha1
        if (dotGradlePath != null) {
            String pathToDependency = dotGradlePath.concat(fileSeparator + groupId + fileSeparator + artifactId + fileSeparator + version);
            File dependencyFolder = new File(pathToDependency);
            // parsing gradle file path, get file hash from its path. the dependency folder version contains
            // 2 folders one for pom and another for the jar. Look for the one with the jar in order to get the sha1
            // .gradle\caches\modules-2\files-2.1\junit\junit\4.12\2973d150c0dc1fefe998f834810d68f278ea58ec
            if (dependencyFolder.isDirectory()) {
                outerloop:
                for (File folder : dependencyFolder.listFiles()) {
                    if (folder.isDirectory()) {
                        for (File file : folder.listFiles()) {
                            if ((file.getName().contains(JAR_EXTENSION) || file.getName().contains(AAR_EXTENTION)) && !file.getName().contains("-sources")) {
                                String pattern = Pattern.quote(fileSeparator);
                                String[] splitFileName = folder.getName().split(pattern);
                                sha1 = splitFileName[splitFileName.length - 1];
                                break outerloop;
                            }
                        }
                    }
                }
            }
        }
        if (sha1 == null){
            logger.debug("Couldn't find sha1 for " + groupId + "." + artifactId + "." + version + " inside .gradle cache." );
        }
        return sha1;
    }

    private String getSha1FromM2(DependencyInfo dependencyInfo){
        String groupId = dependencyInfo.getGroupId();
        String artifactId = dependencyInfo.getArtifactId();
        String version = dependencyInfo.getVersion();
        logger.debug("looking for " + groupId + "." + artifactId + "." + version + " in .m2 cache");
        String sha1 = null;
        if (StringUtils.isBlank(M2Path)){
            this.M2Path = getMavenM2Path(DOT);
        }

        String pathToDependency = M2Path.concat(fileSeparator + String.join(fileSeparator,groupId.split("\\.")) + fileSeparator + artifactId + fileSeparator + version
                + fileSeparator + artifactId + DASH + version + JAR_EXTENSION);
        File file = new File(pathToDependency);
        if (file.isFile()) {
            sha1 = getSha1(pathToDependency);
            if (sha1.equals(EMPTY_STRING)) {
                logger.debug("Couldn't calculate sha1 for " + groupId + "." + artifactId + "." + version + ".  ");
            }
        } else {
            logger.debug("Couldn't find sha1 for " + groupId + "." + artifactId + "." + version + " inside .m2 cache." );
        }
        return sha1;
    }

    private boolean downloadDependencies() {
        dependenciesDownloadAttemptPerformed = true;
        if (runAssembleCommand) {
            try {
                logger.info("running 'gradle assemble' command");
                validateJavaFileExistence();
                List<String> lines = gradleCli.runCmd(rootDirectory, gradleCli.getGradleCommandParams(MvnCommand.ASSEMBLE));
                removeTempJavaFolder();
                if (lines != null) {
                    for (String line : lines) {
                        if (line.contains("BUILD SUCCESSFUL")) {
                            return true;
                        }
                    }
                }
            } catch (IOException e){
                logger.debug("Failed running 'gradle assemble' command, got exception: " + e.getMessage());
            }
        } else {
            logger.debug("Can't run 'gradle assemble' to download missing dependencies.  Change 'gradle.runAssembleCommand' in the configuration file to 'true'");
        }
        logger.error("Failed running 'gradle assemble' command");
        return false;
    }

    private void validateJavaFileExistence() throws IOException{
        // the 'gradle assemble' command, existence of a java file (even empty) inside 'src/java/main' is required.
        // therefore, verifying the file and the path exist - if not creating it, and after running the assemble command removing the item that was added
        String javaDirPath = rootDirectory + this.javaDirPath;
        File javaDir = new File(javaDirPath);
        String srcDirPath = rootDirectory + this.srcDirPath;
        File srcDir = new File(srcDirPath);
        removeSrcDir = false;
        if (!srcDir.isDirectory()){ // src folder doesn't exist - create the whole tree
            FileUtils.forceMkdir(javaDir);
            removeSrcDir = true;
        } else {
            String mainDirPath = rootDirectory + this.mainDirPath;
            File mainDir = new File(mainDirPath);
            removeMainDir = false;
            if (!mainDir.isDirectory()){ // main folder doesn't exist - create it with its sub-folder
                FileUtils.forceMkdir(javaDir);
                removeMainDir = true;
            } else {
                removeJavaDir = false;
                if (!javaDir.isDirectory()) { // java folder doesn't exist - create it
                    FileUtils.forceMkdir(javaDir);
                    removeJavaDir = true;
                }
            }
        }
        removeJavaFile = false;
        if (!javaFileExists(rootDirectory + this.javaDirPath)){ // the java folder doesn't have any java file inside it - creating a temp file
            File javaFile = new File(javaDirPath + fileSeparator + TMP_JAVA_FILE);
            removeJavaFile = javaFile.createNewFile();
        }
    }

    private boolean javaFileExists(String directoryName){
        File directory = new File(directoryName);
        File[] fList = directory.listFiles();
        if (fList != null){
            for (File file : fList){
                if (file.isFile()){
                    if (file.getName().endsWith(JAVA_EXTENSION))
                        return true;
                } else if (file.isDirectory()){
                    return javaFileExists(file.getAbsolutePath());
                }
            }
        }
        return false;
    }

    // removing only the folders/ file that were created
    private void removeTempJavaFolder() throws IOException {
        if (removeJavaDir) {
            FileUtils.forceDelete(new File(rootDirectory + this.javaDirPath));
        } else if (removeMainDir) {
            FileUtils.forceDelete(new File(rootDirectory + this.mainDirPath));
        } else if (removeSrcDir){
            FileUtils.forceDelete(new File(rootDirectory + this.srcDirPath));
        } else if (removeJavaFile){
            FileUtils.forceDelete(new File(rootDirectory + javaDirPath + fileSeparator + TMP_JAVA_FILE));
        }
    }
}
