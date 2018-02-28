package org.whitesource.agent.dependency.resolver.gradle;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.File;
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
public class GradleLinesParser {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(GradleLinesParser.class);
    private static final String PLUS = "+---";
    private static final String SLASH = "\\---";
    private static final String SPACE = " ";
    private static final String PIPE = "|";
    private static final String USER_HOME = "user.home";
    public static final String COLON = ":";
    public static final int INDENTETION_SPACE = 5;
    public static final String EMPTY_STRING = "";
    public static final String FILE_SEPARATOR = "file.separator";
    public static final String JAR_EXTENSION = ".jar";
    public static final String ASTERIX = "(*)";

    private String dotGradlePath;

    public GradleLinesParser(){
        if (StringUtils.isBlank(dotGradlePath)){
            this.dotGradlePath = getDotGradleFolderPath();
        }
    }

    public List<DependencyInfo> parseLines(List<String> lines) {
        if (this.dotGradlePath == null){
            return new ArrayList<>();
        }
        List<String> projectsLines = lines.stream()
                .filter(line->(line.contains(PLUS) || line.contains(SLASH) || line.contains(PIPE)) && !line.contains(ASTERIX))
                .collect(Collectors.toList());

        logger.info("Start parsing gradle dependencies");
        List<DependencyInfo> dependenciesList = new ArrayList<>();
        Stack<DependencyInfo> parentDependencies = new Stack<>();
        List<String> sha1s = new ArrayList<>();
        int prevLineIndentetion = 0;
        boolean duplicateDependency = false;
        for (String line : projectsLines){
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
            String sha1 = getSha1(groupId, artifactId, version);
            if (sha1 == null || sha1s.contains(sha1))
                continue;
            sha1s.add(sha1);
            currentDependency.setSha1(sha1);
            if (dependenciesList.contains(currentDependency)){
                duplicateDependency = true;
                continue;
            }
            // In case the dependency is transitive/child dependency
            if (line.startsWith(SPACE) || line.startsWith(PIPE)){
                if (duplicateDependency)
                    continue;
                if (!parentDependencies.isEmpty()) {
                    // Check if 2 dependencies are siblings (under the hierarchy level)
                    if (lastSpace == prevLineIndentetion){
                        parentDependencies.pop();

                    } else if (lastSpace < prevLineIndentetion) {
                    // Find father dependency of current node
                    /*+--- org.webjars.npm:isurl:1.0.0
                      |    +--- org.webjars.npm:has-to-string-tag-x:[1.2.0,2) -> 1.4.1
                      |    |    \--- org.webjars.npm:has-symbol-support-x:[1.4.1,2) -> 1.4.1
                      |    \--- org.webjars.npm:is-object:[1.0.1,2) -> 1.0.1
                    */
                        while (prevLineIndentetion > lastSpace - INDENTETION_SPACE){
                            parentDependencies.pop();
                            prevLineIndentetion -= INDENTETION_SPACE;
                        }
                    }
                    parentDependencies.peek().getChildren().add(currentDependency);
                }
                parentDependencies.push(currentDependency);
            } else {
                duplicateDependency = false;
                dependenciesList.add(currentDependency);
                parentDependencies.clear();
                parentDependencies.push(currentDependency);
            }
            prevLineIndentetion = lastSpace;
        };

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

    private String getSha1(String groupId, String artifactId, String version){
        String sha1 = null;
        // gradle file path includes the sha1
        String fileSeparator = System.getProperty(FILE_SEPARATOR);
        if (dotGradlePath != null) {
            String pathToDependency = dotGradlePath.concat(fileSeparator + groupId + fileSeparator + artifactId + fileSeparator + version);
            File dependencyFolder = new File(pathToDependency);
            try {
                // parsing gradle file path, get file hash from its path. the dependency folder version contains
                // 2 folders one for pom and another for the jar. Look for the one with the jar in order to get the sha1
                // .gradle\caches\modules-2\files-2.1\junit\junit\4.12\2973d150c0dc1fefe998f834810d68f278ea58ec
                outerloop:
                for (File folder : dependencyFolder.listFiles()) {
                    if (folder.isDirectory()) {
                        for (File file : folder.listFiles()) {
                            if (file.getName().contains(JAR_EXTENSION) && !file.getName().contains("-sources")) {
                                String pattern = Pattern.quote(fileSeparator);
                                String[] splittedFileName = folder.getName().split(pattern);
                                sha1 = splittedFileName[splittedFileName.length - 1];
                                break outerloop;
                            }
                        }
                    }
                }
                if (sha1 == null){
                    logger.error("Couldn't find sha1 for " + groupId + "." + artifactId + "." + version + ".  " + " Run 'gradle build' command and then try again.");
                }
            } catch (NullPointerException ex) {
                logger.error("Couldn't find sha1 for " + groupId + "." + artifactId + "." + version + ".  " +
                        "Make sure it is found inside your " + dotGradlePath + " folder");
            }
        }
        return sha1;
    }

}
