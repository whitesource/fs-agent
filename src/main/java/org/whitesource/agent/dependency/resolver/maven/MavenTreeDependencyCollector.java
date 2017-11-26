package org.whitesource.agent.dependency.resolver.maven;
import fr.dutra.tools.maven.deptree.core.InputType;
import fr.dutra.tools.maven.deptree.core.Node;
import fr.dutra.tools.maven.deptree.core.ParseException;
import fr.dutra.tools.maven.deptree.core.Parser;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.dependency.resolver.DependencyCollector;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Collect dependencies using 'npm ls' or bower command.
 *
 * @author eugen.horovitz
 */
public class MavenTreeDependencyCollector implements DependencyCollector {

/* --- Statics Members --- */

    private static final Logger logger = LoggerFactory.getLogger(org.whitesource.agent.dependency.resolver.maven.MavenTreeDependencyCollector.class);

    public static final String LS_COMMAND = "dependency:tree";
    //public static final String LS_PARAMETER_JSON = ":tree";

    private static final String NPM_COMMAND = isWindows() ? "mvn.cmd" : "mvn";
    private static final String OS_NAME = "os.name";
    private static final String WINDOWS = "win";
    //private static final String DEPENDENCIES = "dependencies";
    //private static final String VERSION = "version";
    //private static final String RESOLVED = "resolved";
    //private static final String LS_ONLY_PROD_ARGUMENT = "--only=prod";
    //private static final String MISSING = "missing";
    //public static final String PEER_MISSING = "peerMissing";
    //private static final String NAME = "name";

/* --- Members --- */

    protected final boolean includeDevDependencies;
    private boolean showNpmLsError;

/* --- Constructors --- */

    public MavenTreeDependencyCollector(boolean includeDevDependencies) {
        this.includeDevDependencies = includeDevDependencies;
    }

/* --- Public methods --- */

    @Override
    public Collection<DependencyInfo> collectDependencies(String rootDirectory) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        try {
            // execute 'npm ls'
            ProcessBuilder pb = new ProcessBuilder(getLsCommandParams());

            pb.directory(new File(rootDirectory));
            Process process = pb.start();

            // parse 'npm ls' output
            //String json = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                //json = reader.lines().reduce("", String::concat);

                //List<String> lines = reader.lines().collect(Collectors.toList());

                //reader.lines().forEach(x->{
                //    if (x.equals(" [INFO] --- maven-dependency-plugin:2.8:tree (default-cli) @ wss-common ---")){
//
                //    }
                //});

                List<List<String>> lines2 = reader.lines()
                        .map(x->x.replace("[INFO] ",""))
                        .collect(splitBySeparator(x-> {
                    if (x.contains("maven-dependency-plugin:2.8:tree")) {
                        return true;
                    } else {
                        return false;
                    }
                }));

                List<Node> nodes = new ArrayList<>();
                lines2.forEach(x->{
                    String json = String.join(System.lineSeparator(),x);
                    InputStream is = null;
                    Reader r;
                    try {
                        is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8.name()));
                        r = new InputStreamReader(is, "UTF-8");
                        Parser parser = InputType.TEXT.newParser();
                        Node tree = parser.parse(r);
                        nodes.add(tree);

                        String s ="";
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                });


                        //reader.lines().collect(Collectors.partitioningBy(s -> s.equals(" [INFO] --- maven-dependency-plugin:2.8:tree (default-cli) @ wss-common ---")));

                //json = reader.lines().reduce(System.lineSeparator(), String::concat);
                reader.close();

                Node n = nodes.stream().max(Comparator.comparingInt(x->x.getChildNodes().size())).get();

                return n.getChildNodes().stream().map(x->getDependencyFromNode(x)).collect(Collectors.toList());
                //List<List<String>> groups =
            } catch (IOException e) {
                logger.error("error parsing output : {}", e.getMessage());
            }

            //if (StringUtils.isNotBlank(json)) {
            //    //dependencies.addAll(getDependencies(new JSONObject(json)));
            //}

        } catch (IOException e) {
            logger.info("Error getting dependencies after running 'npm ls --json' on {}", rootDirectory);
        }

        if (dependencies.isEmpty()) {
            if (!showNpmLsError) {
                logger.info("Failed getting dependencies after running '{}' Please run 'npm install' on the folder {}", getLsCommandParams(), rootDirectory);
                showNpmLsError = true;
            }
        }
        return dependencies;
    }

    private DependencyInfo getDependencyFromNode(Node node) {
        DependencyInfo d =  new DependencyInfo(node.getGroupId(),node.getArtifactId(),node.getVersion());
        node.getChildNodes().forEach(y->d.getChildren().add(getDependencyFromNode(y)));
        return d;
    }

    private static Collector<String, List<List<String>>, List<List<String>>> splitBySeparator(Predicate<String> sep) {
        return Collector.of(() -> new ArrayList<List<String>>(Arrays.asList(new ArrayList<>())),
                (l, elem) -> {if(sep.test(elem)){l.add(new ArrayList<>());} else l.get(l.size()-1).add(elem);},
                (l1, l2) -> {l1.get(l1.size() - 1).addAll(l2.remove(0)); l1.addAll(l2); return l1;});
    }

/* --- Private methods --- */

/* --- Protected methods --- */

    protected String[] getLsCommandParams() {
        //if (includeDevDependencies) {
        //    return new String[]{NPM_COMMAND, LS_COMMAND, LS_PARAMETER_JSON};
        //} else {
        return new String[]{NPM_COMMAND, LS_COMMAND};
        //}
    }

    protected DependencyInfo getDependency(String dependencyAlias, JSONObject jsonObject) {
        DependencyInfo dependency = new DependencyInfo();
        //dependency.setGroupId(name);
        //dependency.setArtifactId(filename);
        //dependency.setVersion(version);
        //dependency.setFilename(filename);
        //dependency.setDependencyType(DependencyType.NPM);
        return dependency;
    }

/* --- Static methods --- */

    public static boolean isWindows() {
        return System.getProperty(OS_NAME).toLowerCase().contains(WINDOWS);
    }
}
