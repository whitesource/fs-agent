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
    public static final String UTF_8 = "UTF-8";
    public static final String SCOPE_COMPILE = "compile";
    public static final String SCOPE_RUNTIME = "runtime";
    public static final String DOT = ".";
    public static final String DASH = "-";

    /* --- Members --- */

    private final boolean includeDevDependencies;
    private String M2Path;
    private boolean showMavenTreeError;

    /* --- Constructors --- */

    public MavenTreeDependencyCollector(boolean includeDevDependencies) {
        this.includeDevDependencies = includeDevDependencies;
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
                    .map(line -> line.replace(INFO, ""))
                    .collect(splitBySeparator(formattedLines -> formattedLines.contains(MAVEN_DEPENDENCY_PLUGIN_TREE)));

            List<Node> nodes = new ArrayList<>();
            projectsLines.forEach(singleProjectLines -> {
                String json = String.join(System.lineSeparator(), singleProjectLines);
                try (InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8.name()));
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
                    logger.debug("error parsing output : {} {}", e.getMessage(), json);
                }
            });

            projects = nodes.stream().filter(node -> node.getChildNodes().size() > 0).map(tree -> {
                List<DependencyInfo> dependencies = new LinkedList<>();
                Stream<Node> nodeStream;
                if (includeDevDependencies) {
                    nodeStream = tree.getChildNodes().stream();
                } else {
                    nodeStream = tree.getChildNodes().stream().filter(node -> node.getScope().equals(SCOPE_COMPILE) || node.getScope().equals(SCOPE_RUNTIME));
                }
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

        if (projects != null && projects.size() > 0) {
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
            return null;
        }
    }

    private DependencyInfo getDependencyFromNode(Node node, Map<String,List<DependencyInfo>> paths ) {
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
        dependency.setSystemPath(filePath);
        dependency.setFilename(filePath);

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
        String[] params = new String[]{MVN_COMMAND,MVN_PARAMS_M2PATH_PATH ,MVN_PARAMS_M2PATH_LOCAL};
        try {
            List<String> lines = getExternalProcessOutput(rootDirectory, params);
            Optional<String> pathLine = lines.stream().filter(line->(new File(line).exists())).findFirst();
            if(pathLine.isPresent()){
                return  pathLine.get();
            }else {
                logger.error("could not get m2 path : {} out: {}", rootDirectory, lines.stream().reduce("", String::concat));
                return null;
            }
        } catch (IOException io){
            logger.error("could not get m2 path : {}", io.getMessage());
            showMavenTreeError = true;
            return null;
        }
    }

    private List<String> getExternalProcessOutput(String rootDirectory, String[] processWithArgs) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(processWithArgs);
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
