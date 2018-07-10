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
package org.whitesource.agent.dependency.resolver.maven;

import fr.dutra.tools.maven.deptree.core.Node;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.hash.ChecksumUtils;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collect dependencies using 'npm ls' or bower command.
 *
 * @author eugen.horovitz
 */
public class MavenTreeDependencyCollector extends DependencyCollector {

    /* --- Statics Members --- */

    private static final Logger logger = LoggerFactory.getLogger(org.whitesource.agent.dependency.resolver.maven.MavenTreeDependencyCollector.class);

    private static final String MVN_PARAMS_M2PATH_PATH = "help:evaluate";
    private static final String MVN_PARAMS_M2PATH_LOCAL = "-Dexpression=settings.localRepository";
    private static final String MVN_PARAMS_TREE = "dependency:tree";
    private static final String MVN_COMMAND = "mvn";
    private static final String SCOPE_TEST = "test";
    private static final String SCOPE_PROVIDED = "provided";
    private static final String USER_HOME = "user.home";
    private static final String M2 = ".m2";
    private static final String REPOSITORY = "repository";
    private static final String ALL = "All";
    private static final String POM = "pom";
    public static final String TEST_JAR = "test-jar";
    public static final String JAR = "jar";

    /* --- Members --- */

    protected String M2Path;
    private final Set<String> mavenIgnoredScopes;
    private boolean showMavenTreeError;
    private boolean ignorePomModules;
    private MavenLinesParser mavenLinesParser;

    /* --- Constructors --- */

    public MavenTreeDependencyCollector(String[] mavenIgnoredScopes, boolean ignorePomModules) {
        mavenLinesParser = new MavenLinesParser();
        this.mavenIgnoredScopes = new HashSet<>();
        if (mavenIgnoredScopes == null) {
            this.mavenIgnoredScopes.add(SCOPE_PROVIDED);
            this.mavenIgnoredScopes.add(SCOPE_TEST);
        } else {
            if (mavenIgnoredScopes.length == 1 && mavenIgnoredScopes[0].equals(ALL)) {
                // do not filter out any scope
            } else {
                Arrays.stream(mavenIgnoredScopes).filter(exclude -> StringUtils.isBlank(exclude))
                        .map(exclude -> this.mavenIgnoredScopes.add(exclude));
            }
        }
        this.ignorePomModules = ignorePomModules;
    }

    /* --- Public methods --- */

    @Override
    public Collection<AgentProjectInfo> collectDependencies(String rootDirectory) {
        if (StringUtils.isBlank(M2Path)){
            this.M2Path = getMavenM2Path(Constants.DOT);
        }

        Map<String, List<DependencyInfo>> pathToDependenciesMap = new HashMap<>();
        Collection<AgentProjectInfo> projects = new ArrayList<>();
        try {
            CommandLineProcess mvnDependencies = new CommandLineProcess(rootDirectory, getLsCommandParams());
            List<String> lines = mvnDependencies.executeProcess();
            if (!mvnDependencies.isErrorInProcess()) {
                List<Node> nodes = mavenLinesParser.parseLines(lines);

                logger.info("End parsing pom files , found : " + String.join(Constants.COMMA,
                        nodes.stream().map(node -> node.getArtifactId()).collect(Collectors.toList())));

                projects = nodes.stream()
                        .filter(node -> !this.ignorePomModules || (ignorePomModules && !node.getPackaging().equals(POM)))
                        .map(tree -> {

                    List<DependencyInfo> dependencies = new LinkedList<>();
                    Stream<Node> nodeStream = tree.getChildNodes().stream().filter(node -> !mavenIgnoredScopes.contains(node.getScope()));
                    dependencies.addAll(nodeStream.map(node -> getDependencyFromNode(node, pathToDependenciesMap)).collect(Collectors.toList()));

                    Map<String, String> pathToSha1Map = pathToDependenciesMap.keySet().stream().distinct().parallel().collect(Collectors.toMap(file -> file, file -> getSha1(file)));
                    pathToSha1Map.entrySet().forEach(pathSha1Pair -> pathToDependenciesMap.get(pathSha1Pair.getKey()).stream().forEach(dependency -> {
                        dependency.setSha1(pathSha1Pair.getValue());
                        dependency.setSystemPath(pathSha1Pair.getKey());
                    }));

                    AgentProjectInfo projectInfo = new AgentProjectInfo();
                    projectInfo.setCoordinates(new Coordinates(tree.getGroupId(), tree.getArtifactId(), tree.getVersion()));
                    dependencies.stream().filter(dependency -> StringUtils.isNotEmpty(dependency.getSha1())).forEach(dependency ->
                            projectInfo.getDependencies().add(dependency));
                    return projectInfo;
                }).collect(Collectors.toList());
            } else {
                logger.warn("Failed to scan and send {}", getLsCommandParams());
            }
        } catch (IOException e) {
            logger.warn("Error getting dependencies after running {} on {}, {}" , getLsCommandParams() , rootDirectory, e.getMessage());
            logger.debug("Error: {}", e.getStackTrace());
        }

        if (projects != null && projects.isEmpty()) {
            if (!showMavenTreeError) {
                logger.warn("Failed to getting dependencies after running '{}' Please install maven ", getLsCommandParams());
                showMavenTreeError = true;
            }
        }
        return projects;
    }

    protected String getSha1(String filePath) {
        try {
            return ChecksumUtils.calculateSHA1(new File(filePath));
        } catch (IOException e) {
            logger.warn("Failed getting " + filePath + ". File will not be sent to WhiteSource server.");
            return Constants.EMPTY_STRING;
        }
    }

    private DependencyInfo getDependencyFromNode(Node node, Map<String,List<DependencyInfo>> paths ) {
        logger.debug("converting node to dependency :" + node.getArtifactId());
        DependencyInfo dependency = new DependencyInfo(node.getGroupId(), node.getArtifactId(), node.getVersion());
        dependency.setDependencyType(DependencyType.MAVEN);
        dependency.setScope(node.getScope());
        dependency.setType(node.getPackaging());

        String shortName;
        if (StringUtils.isBlank(node.getClassifier())) {
            shortName = dependency.getArtifactId() + Constants.DASH + dependency.getVersion() + Constants.DOT + node.getPackaging();
        } else {
            String nodePackaging = node.getPackaging();
            if (nodePackaging.equals(TEST_JAR)){
                nodePackaging = JAR;
            }
            shortName = dependency.getArtifactId() + Constants.DASH + dependency.getVersion() + Constants.DASH + node.getClassifier() + Constants.DOT + nodePackaging;
        }

        String filePath = Paths.get(M2Path, dependency.getGroupId().replace(Constants.DOT, File.separator), dependency.getArtifactId(), dependency.getVersion(), shortName).toString();
        if (!paths.containsKey(filePath)) {
            paths.put(filePath, new ArrayList<>());
        }
        paths.get(filePath).add(dependency);
        if (StringUtils.isNotBlank(filePath)) {
            File jarFile = new File(filePath);
            if(jarFile.exists()) {
                dependency.setFilename(jarFile.getName());
            }
        }

        node.getChildNodes().forEach(childNode -> dependency.getChildren().add(getDependencyFromNode(childNode, paths)));
        return dependency;
    }

    /* --- Private methods --- */

    private String[] getLsCommandParams() {
        if (isWindows()) {
            return new String[] {Constants.CMD, C_CHAR_WINDOWS, MVN_COMMAND, MVN_PARAMS_TREE};
        } else {
            return new String[] {MVN_COMMAND, MVN_PARAMS_TREE};
        }
    }

    protected String getMavenM2Path(String rootDirectory) {
        String currentUsersHomeDir = System.getProperty(USER_HOME);
        File m2Path = Paths.get(currentUsersHomeDir, M2, REPOSITORY).toFile();

        if (m2Path.exists()) {
            return m2Path.getAbsolutePath();
        }
        String[] params = null;
        if (isWindows()) {
            params = new String[]{Constants.CMD, C_CHAR_WINDOWS, MVN_COMMAND, MVN_PARAMS_M2PATH_PATH, MVN_PARAMS_M2PATH_LOCAL};
        } else {
            params = new String[]{MVN_COMMAND, MVN_PARAMS_M2PATH_PATH, MVN_PARAMS_M2PATH_LOCAL};
        }
        try {
            CommandLineProcess mvnProcess = new CommandLineProcess(rootDirectory, params);
            List<String> lines = mvnProcess.executeProcess();
            if (!mvnProcess.isErrorInProcess()) {
                Optional<String> pathLine = lines.stream().filter(line -> (new File(line).exists())).findFirst();
                if (pathLine.isPresent()) {
                    return pathLine.get();
                } else {
                    logger.warn("could not get m2 path : {} out: {}", rootDirectory, lines.stream().reduce(Constants.EMPTY_STRING, String::concat));
                    showMavenTreeError = true;
                    return null;
                }
            } else {
                logger.warn("Failed to scan and send {}", getLsCommandParams());
                return null;
            }
        } catch (IOException io) {
            logger.warn("could not get m2 path : {}", io.getMessage());
            showMavenTreeError = true;
            return null;
        }
    }
}
