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
 * @author eugen.horovitz
 */
public class GradleLinesParser {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(GradleLinesParser.class);
    private static final String PLUS = "+---";
    private static final String SLASH = "\\---";
    private static final String SPACE = " ";
    private static final String PIPE = "|";
    private static final String USER_HOME = "user.home";

    private String dotGradlePath;

    public GradleLinesParser(){
        if (StringUtils.isBlank(dotGradlePath)){
            this.dotGradlePath = getDotGradlePath();
        }
    }

    public List<DependencyInfo> parseLines(List<String> lines) {
        List<String> projectsLines = lines.stream()
                .filter(line->line.contains(PLUS) || line.contains(SLASH) || line.contains(PIPE))
                //.distinct()
                .collect(Collectors.toList());

        logger.info("Start parsing pom files");
        List<DependencyInfo> dependenciesList = new ArrayList<>();
        Stack<DependencyInfo> parentDependencies = new Stack<>();
        int prevLastSpace = 0;
        boolean duplicateDependency = false;
        for (String line : projectsLines){
            String[] strings = line.split(":");
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
            DependencyInfo currentDependency = new DependencyInfo(groupId, artifactId, version);
            String sha1 = getSha1(groupId, artifactId, version);
            currentDependency.setSha1(sha1);
            if (dependenciesList.contains(currentDependency)){
                duplicateDependency = true;
                continue;
            }
            if (line.startsWith(SPACE) || line.startsWith(PIPE)){
                if (duplicateDependency)
                    continue;;
                if (!parentDependencies.isEmpty()) {
                    if (lastSpace == prevLastSpace){
                        parentDependencies.pop();
                    } else if (lastSpace < prevLastSpace) {
                        while (prevLastSpace > lastSpace - 5){
                            parentDependencies.pop();
                            prevLastSpace -= 5;
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
            prevLastSpace = lastSpace;
        };

        return dependenciesList;
    }

    private String getDotGradlePath() {
        String currentUsersHomeDir = System.getProperty(USER_HOME);
        File dotGradle = Paths.get(currentUsersHomeDir, ".gradle", "caches","modules-2","files-2.1").toFile();

        if (dotGradle.exists()) {
            return dotGradle.getAbsolutePath();
        }
        logger.error("could not get .gradle path");
        return  null;
    }

    private String getSha1(String groupId, String artifactId, String version){
        String fileSeperator = System.getProperty("file.separator");
        String pathToDependency = dotGradlePath.concat(fileSeperator + groupId + fileSeperator + artifactId + fileSeperator + version);
        File dependencyFolder = new File(pathToDependency);
        String sha1 = "";
        try {
            for (File folder : dependencyFolder.listFiles()) {
                if (folder.isDirectory()) {
                    for (File file : folder.listFiles()) {
                        if (file.getName().contains(".jar")) {
                            String pattern = Pattern.quote(fileSeperator);
                            String[] splittedFileName = folder.getName().split(pattern);
                            sha1 = splittedFileName[splittedFileName.length - 1];
                        }
                    }
                }
            }
        } catch (NullPointerException ex){
            logger.error("Couldn't find sha1 for " + groupId + "." + artifactId + "." + version + ".  Make sure it is found inside your " + dotGradlePath + " folder");
        }
        return sha1;
    }

    private boolean isOffspring(DependencyInfo ancestor, DependencyInfo offspring){
        if (ancestor.getChildren().size() == 0){
            return false;
        }
        if (ancestor.getChildren().contains(offspring)){
            return true;
        }
        for (DependencyInfo dependencyInfo : ancestor.getChildren()){
            if (isOffspring(dependencyInfo, offspring))
                return true;
        }
        return false;
    }
}
