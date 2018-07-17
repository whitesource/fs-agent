package org.whitesource.agent.dependency.resolver.gradle;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.maven.MavenTreeDependencyCollector;
import org.whitesource.agent.utils.FilesUtils;

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

    private final Logger logger = LoggerFactory.getLogger(GradleLinesParser.class);
    private static final String TMP_JAVA_FILE = "tmp.java";
    private static final String MAIN = "main";
    private static final String JAVA = "java";
    private static final String JAVA_EXTENSION = ".java";
    private static final String AAR_EXTENTION = ".aar";
    private static final String PLUS = "+---";
    private static final String SLASH = "\\---";
    private static final String USER_HOME = "user.home";
    private static final int INDENTETION_SPACE = 5;
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
        super(null, true);
        this.runAssembleCommand = runAssembleCommand;
        gradleCli = new GradleCli();
        fileSeparator = System.getProperty(Constants.FILE_SEPARATOR);
        srcDirPath = fileSeparator + Constants.SRC;
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
        logger.info("Start parsing gradle dependencies of: {}", rootDirectory);
        List<String> projectsLines = lines.stream()
                .filter(line->(line.contains(PLUS) || line.contains(SLASH) || line.contains(Constants.PIPE)) && !line.contains(ASTERIX))
                .collect(Collectors.toList());
        List<DependencyInfo> dependenciesList = new ArrayList<>();
        Stack<DependencyInfo> parentDependencies = new Stack<>();
        List<String> sha1s = new ArrayList<>();
        int prevLineIndentation = 0;
        boolean duplicateDependency = false;
        for (String line : projectsLines){
            if (line.indexOf(Constants.COLON) == -1 || line.contains("project :")){
                continue;
            }
            String[] strings = line.split(Constants.COLON);
            String groupId = strings[0];
            int lastSpace = groupId.lastIndexOf(Constants.WHITESPACE);
            groupId = groupId.substring(lastSpace + 1);
            String artifactId = strings[1];
            String version = strings[2];
            if (version.contains(Constants.WHITESPACE)){
                if (version.contains("->")){
                    version = version.split(Constants.WHITESPACE)[version.split(Constants.WHITESPACE).length-1];
                } else {
                    version = version.split(Constants.WHITESPACE)[0];
                }
            }
            // Create dependencyInfo & calculate SHA1
            DependencyInfo currentDependency = new DependencyInfo(groupId, artifactId, version);
            DependencyFile dependencyFile = getDependencySha1(currentDependency);
            if (dependencyFile == null || dependencyFile.getSha1().equals(Constants.EMPTY_STRING) || sha1s.contains(dependencyFile.getSha1()))
                continue;
            sha1s.add(dependencyFile.getSha1());
            currentDependency.setSha1(dependencyFile.getSha1());
            currentDependency.setSystemPath(dependencyFile.getFilePath());
            currentDependency.setFilename(dependencyFile.getFileName());
            currentDependency.setDependencyType(DependencyType.GRADLE);

            String extension = FilesUtils.getFileExtension(dependencyFile.getFilePath());
            currentDependency.setType(extension);

            if (dependenciesList.contains(currentDependency)){
                duplicateDependency = true;
                continue;
            }
            // In case the dependency is transitive/child dependency
            if (line.startsWith(Constants.WHITESPACE) || line.startsWith(Constants.PIPE)){
                if (duplicateDependency || parentDependencies.isEmpty()) {
                    continue;
                }
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
                if(!parentDependencies.isEmpty())
                    parentDependencies.peek().getChildren().add(currentDependency);
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

    private DependencyFile getDependencySha1(DependencyInfo dependencyInfo){
        DependencyFile dependencyFile = getSha1FromGradleCache(dependencyInfo);
        if (dependencyFile == null){
            // if dependency not found in .gradle cache - looking for it in .m2 cache
            dependencyFile = getSha1FromM2(dependencyInfo);
            if (dependencyFile == null || dependencyFile.getSha1().equals(Constants.EMPTY_STRING)){
                // if dependency not found in .m2 cache - running 'gradel assemble' command which should download the dependency to .grade cache
                // making sure the download attempt is performed only once, otherwise there might be an infinite loop
                if (!dependenciesDownloadAttemptPerformed && downloadDependencies()){
                    dependencyFile = getDependencySha1(dependencyInfo);
                } else {
                    logger.error("Couldn't find sha1 for " + dependencyInfo.getGroupId() + Constants.DOT +
                            dependencyInfo.getArtifactId() + Constants.DOT + dependencyInfo.getVersion());
                }
            }
        }
        return dependencyFile;
    }

    private DependencyFile getSha1FromGradleCache(DependencyInfo dependencyInfo){
        String groupId = dependencyInfo.getGroupId();
        String artifactId = dependencyInfo.getArtifactId();
        String version = dependencyInfo.getVersion();
        logger.debug("looking for " + groupId + "." + artifactId + "." + version + " in .gradle cache");
        DependencyFile dependencyFile = null;
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
                                String sha1 = splitFileName[splitFileName.length - 1];
                                dependencyFile = new DependencyFile(sha1,file);
                                break outerloop;
                            }
                        }
                    }
                }
            }
        }
        if (dependencyFile == null){
            logger.debug("Couldn't find sha1 for " + groupId + Constants.DOT + artifactId + Constants.DOT + version + " inside .gradle cache." );
        }
        return dependencyFile;
    }

    private DependencyFile getSha1FromM2(DependencyInfo dependencyInfo){
        String groupId = dependencyInfo.getGroupId();
        String artifactId = dependencyInfo.getArtifactId();
        String version = dependencyInfo.getVersion();
        logger.debug("looking for " + groupId + "." + artifactId + "." + version + " in .m2 cache");
        DependencyFile dependencyFile = null;
        if (StringUtils.isBlank(M2Path)){
            this.M2Path = getMavenM2Path(Constants.DOT);
        }

        String pathToDependency = M2Path.concat(fileSeparator + String.join(fileSeparator,groupId.split("\\.")) +
                fileSeparator + artifactId + fileSeparator + version
                + fileSeparator + artifactId + Constants.DASH + version + JAR_EXTENSION);
        File file = new File(pathToDependency);
        if (file.isFile()) {
            String sha1 = getSha1(pathToDependency);
            dependencyFile = new DependencyFile(sha1,file);
            if (sha1.equals(Constants.EMPTY_STRING)) {
                logger.debug("Couldn't calculate sha1 for " + groupId + Constants.DOT + artifactId + Constants.DOT + version + ".  ");
            }
        } else {
            logger.debug("Couldn't find sha1 for " + groupId + Constants.DOT + artifactId + Constants.DOT + version + " inside .m2 cache." );
        }
        return dependencyFile;
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

    private class DependencyFile {
       private String sha1;
       private String filePath;
       private String fileName;

       public DependencyFile(String sha1, File file){
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
