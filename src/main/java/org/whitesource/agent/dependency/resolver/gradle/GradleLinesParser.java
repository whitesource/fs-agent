package org.whitesource.agent.dependency.resolver.gradle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
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
    public static final String EMPTY_STRING = "";
    public static final String SPACE = " ";
    public static final String PIPE = "|";

    public List<DependencyInfo> parseLines(List<String> lines) {
        List<String> projectsLines = lines.stream()
                .filter(line->line.contains(PLUS) || line.contains(SLASH) || line.contains(PIPE))
//                .map(line -> line.replace(PLUS, EMPTY_STRING))
//                .map(line -> line.replace(SLASH,EMPTY_STRING))
//                .map(line -> line.replace("|",EMPTY_STRING))
//                .map(line -> line.trim())
                .distinct()
                .collect(Collectors.toList());

        logger.info("Start parsing pom files");
        List<DependencyInfo> dependenciesList = new ArrayList<>();
        Stack<DependencyInfo> parentDependencies = new Stack<>();
        int prevLastSpace = 0;
        for (String line : projectsLines){
            String[] strings = line.split(":");
            String groupId = strings[0];
            int lastSpace = groupId.lastIndexOf(SPACE);
            groupId = groupId.substring(lastSpace + 1);
            String artifactId = strings[1];
            String version = strings[2];
            if (version.contains(SPACE)){
                version = version.split(SPACE)[version.split(SPACE).length-1];
            }
            DependencyInfo currentDependency = new DependencyInfo(groupId, artifactId, version);
            if (dependenciesList.contains(currentDependency)){
                continue;
            }
            if (line.startsWith(SPACE) || line.startsWith(PIPE)){
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
                dependenciesList.add(currentDependency);
                parentDependencies.empty();
                parentDependencies.push(currentDependency);
            }
            prevLastSpace = lastSpace;
        };

        return dependenciesList;
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
