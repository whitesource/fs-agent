package org.whitesource.agent.dependency.resolver.maven;
import fr.dutra.tools.maven.deptree.core.InputType;
import fr.dutra.tools.maven.deptree.core.Node;
import fr.dutra.tools.maven.deptree.core.ParseException;
import fr.dutra.tools.maven.deptree.core.Parser;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.hash.ChecksumUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collector;
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
    private static final String MVN_COMMAND = isWindows() ? "mvn.cmd" : "mvn";
    private static final String OS_NAME = "os.name";
    private static final String WINDOWS = "win";
    private static final String MAVEN_DEPENDENCY_PLUGIN_TREE = "maven-dependency-plugin:"; // 2.8:tree";
    private static final String INFO = "[INFO] ";
    private static final String UTF_8 = "UTF-8";
    private static final String SCOPE_TEST = "test";
    private static final String SCOPE_PROVIDED = "provided";
    private static final String DOT = ".";
    private static final String DASH = "-";
    private static final String USER_HOME = "user.home";
    private static final String M2 = ".m2";
    private static final String REPOSITORY = "repository";
    private static final String ALL = "All";
    public static final String EMPTY_STRING = "";
    public static final String POM = "pom";


    /* --- Members --- */

    private final Set<String> mavenIgnoredScopes;
    private String M2Path;
    private boolean showMavenTreeError;

    /* --- Constructors --- */

    public MavenTreeDependencyCollector(String[] mavenIgnoredScopes) {
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
    }

    /* --- Public methods --- */

    @Override
    public Collection<AgentProjectInfo> collectDependencies(String rootDirectory) {
        if (StringUtils.isBlank(M2Path)){
            this.M2Path = getMavenM2Path(DOT);
        }

        Map<String, List<DependencyInfo>> pathToDependenciesMap = new HashMap<>();
        Collection<AgentProjectInfo> projects = null;
        try {
            List<String> lines = getExternalProcessOutput(rootDirectory, getLsCommandParams());
            List<List<String>> projectsLines = lines.stream()
                    .map(line -> line.replace(INFO, EMPTY_STRING))
                    .collect(splitBySeparator(formattedLines -> formattedLines.contains(MAVEN_DEPENDENCY_PLUGIN_TREE)));

            logger.info("Start parsing pom files");
            List<Node> nodes = new ArrayList<>();
            projectsLines.forEach(singleProjectLines -> {
                String mvnLines = String.join(System.lineSeparator(), singleProjectLines);
                try (InputStream is = new ByteArrayInputStream(mvnLines.getBytes(StandardCharsets.UTF_8.name()));
                     Reader lineReader = new InputStreamReader(is, UTF_8)) {
                    Parser parser = InputType.TEXT.newParser();
                    Node tree = parser.parse(lineReader);
                    nodes.add(tree);
                } catch (UnsupportedEncodingException e) {
                    logger.error("unsupportedEncoding error parsing output : {}", e.getMessage());
                } catch (ParseException e) {
                    logger.error("error parsing output : {} ", e.getMessage());
                } catch (Exception e) {
                    // this can happen often - some parts of the output are not parsable
                    logger.debug("error parsing output : {} {}", e.getMessage(), mvnLines);
                }
            });

            logger.info("End parsing pom files , found : " + String.join(",",
                    nodes.stream().map(node -> node.getArtifactId()).collect(Collectors.toList())));

            projects = nodes.stream().filter(node -> !node.getPackaging().equals(POM)).map(tree -> {

                List<DependencyInfo> dependencies = new LinkedList<>();
                Stream<Node> nodeStream = tree.getChildNodes().stream().filter(node -> !mavenIgnoredScopes.contains(node.getScope()));
                dependencies.addAll(nodeStream.map(node -> getDependencyFromNode(node, pathToDependenciesMap)).collect(Collectors.toList()));

                Map<String, String> pathToSha1Map = pathToDependenciesMap.keySet().stream().distinct().parallel().collect(Collectors.toMap(file -> file, file -> getSha1(file)));
                pathToSha1Map.entrySet().forEach(pathSha1Pair -> pathToDependenciesMap.get(pathSha1Pair.getKey()).stream().forEach(dependency -> dependency.setSha1(pathSha1Pair.getValue())));

                AgentProjectInfo projectInfo = new AgentProjectInfo();
                projectInfo.setCoordinates(new Coordinates(tree.getGroupId(), tree.getArtifactId(), tree.getVersion()));
                dependencies.stream().forEach(dependency -> projectInfo.getDependencies().add(dependency));
                return projectInfo;
            }).collect(Collectors.toList());

        } catch (IOException e) {
            logger.info("Error getting dependencies after running " + getLsCommandParams() + " on " + rootDirectory, e);
        }

        if (projects != null && projects.isEmpty()) {
            if (!showMavenTreeError) {
                logger.info("Failed getting dependencies after running '{}' Please install maven ", getLsCommandParams());
                showMavenTreeError = true;
            }
        }
        return projects;
    }

    private String getSha1(String filePath) {
        try {
            return  ChecksumUtils.calculateSHA1(new File(filePath));
        } catch (IOException e) {
            logger.info("Failed getting " +filePath, getLsCommandParams());
            return EMPTY_STRING;
        }
    }

    private DependencyInfo getDependencyFromNode(Node node, Map<String,List<DependencyInfo>> paths ) {
        logger.debug("converting node to dependency :" + node.getArtifactId());
        DependencyInfo dependency = new DependencyInfo(node.getGroupId(), node.getArtifactId(), node.getVersion());
        dependency.setDependencyType(DependencyType.MAVEN);
        dependency.setScope(node.getScope());

        String shortName;
        if (StringUtils.isBlank(node.getClassifier())) {
            shortName = dependency.getArtifactId() + DASH + dependency.getVersion() + DOT + node.getPackaging();
        } else {
            shortName = dependency.getArtifactId() + DASH + dependency.getVersion() + DASH + node.getClassifier() + DOT + node.getPackaging();
        }

        String filePath = Paths.get(M2Path, dependency.getGroupId().replace(DOT, File.separator), dependency.getArtifactId(), dependency.getVersion(), shortName).toString();
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

    // so : 29095967
    private static Collector<String, List<List<String>>, List<List<String>>> splitBySeparator(Predicate<String> sep) {
        return Collector.of(() -> new ArrayList<List<String>>(Arrays.asList(new ArrayList<>())),
                (l, elem) -> {if(sep.test(elem)){l.add(new ArrayList<>());} else l.get(l.size()-1).add(elem);},
                (l1, l2) -> {l1.get(l1.size() - 1).addAll(l2.remove(0)); l1.addAll(l2); return l1;});
    }

    /* --- Private methods --- */

    private String[] getLsCommandParams() {
        return new String[]{MVN_COMMAND, MVN_PARAMS_TREE};
    }

    private String getMavenM2Path(String rootDirectory) {
        String currentUsersHomeDir = System.getProperty(USER_HOME);
        File m2Path = Paths.get(currentUsersHomeDir, M2, REPOSITORY).toFile();

        if (m2Path.exists()) {
            return m2Path.getAbsolutePath();
        }

        String[] params = new String[]{MVN_COMMAND, MVN_PARAMS_M2PATH_PATH, MVN_PARAMS_M2PATH_LOCAL};
        try {
            List<String> lines = getExternalProcessOutput(rootDirectory, params);
            Optional<String> pathLine = lines.stream().filter(line -> (new File(line).exists())).findFirst();
            if (pathLine.isPresent()) {
                return pathLine.get();
            } else {
                logger.error("could not get m2 path : {} out: {}", rootDirectory, lines.stream().reduce("", String::concat));
                return null;
            }
        } catch (IOException io) {
            logger.error("could not get m2 path : {}", io.getMessage());
            showMavenTreeError = true;
            return null;
        }
    }

    // todo : refactor all process builder methods with timeout and error handling - (npm resolver)
    private List<String> getExternalProcessOutput(String rootDirectory, String[] processWithArgs) throws IOException {
        // execute 'args'
        ProcessBuilder pb = new ProcessBuilder(getLsCommandParams());
        pb.directory(new File(rootDirectory));
        // redirect the error output to avoid output of npm ls by operating system
        String redirectErrorOutput = isWindows() ? "nul" : "/dev/null";
        pb.redirectError(new File(redirectErrorOutput));
        logger.debug("start "+ processWithArgs);

        pb.directory(new File(rootDirectory));
        Process process = pb.start();

        try (InputStreamReader inputStreamReader = new InputStreamReader(process.getInputStream());
             BufferedReader reader = new BufferedReader(inputStreamReader)) {
            return reader.lines().collect(Collectors.toList());
        }
    }

    /* --- Static methods --- */

    private static boolean isWindows() {
        return System.getProperty(OS_NAME).toLowerCase().contains(WINDOWS);
    }
}
