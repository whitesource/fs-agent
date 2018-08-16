package org.whitesource.agent.dependency.resolver.maven;

import fr.dutra.tools.maven.deptree.core.InputType;
import fr.dutra.tools.maven.deptree.core.Node;
import fr.dutra.tools.maven.deptree.core.ParseException;
import fr.dutra.tools.maven.deptree.core.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.Constants;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collector;

/**
 * This class represents parser for Maven output lines
 *
 * @author eugen.horovitz
 */
public class MavenLinesParser {

    /* --- Static members --- */

    private Logger logger = LoggerFactory.getLogger(MavenLinesParser.class);
    private static final String MAVEN_DEPENDENCY_PLUGIN_TREE = "maven-dependency-plugin:"; // 2.8:tree";
    private static final String INFO = "[INFO] ";
    private static final String UTF_8 = "UTF-8";

    public List<Node> parseLines(List<String> lines) {
        List<List<String>> projectsLines = lines.stream().filter(line -> line.contains(INFO))// && !line.contains(Character.toString(Constants.OPEN_BRACKET)) && !line.endsWith(Character.toString(Constants.CLOSE_BRACKET)))
                .map(line -> line.replace(INFO, Constants.EMPTY_STRING))
                .collect(splitBySeparator(formattedLines -> formattedLines.contains(MAVEN_DEPENDENCY_PLUGIN_TREE)));

        logger.info("Start parsing pom files");
        List<Node> nodes = new ArrayList<>();
        projectsLines.forEach(singleProjectLines -> {
            // for cases such as the output_log.txt in WSE-600, where the first line of the block of output can't be parsed
            // or cases like WSE-730, where verbose=true and maven-dependency-plugin >= 3, where the first line of hte block of output can't be parsed
            if (singleProjectLines.get(0).contains(Constants.COLON) || (singleProjectLines.get(1) != null && singleProjectLines.get(1).contains(Constants.COLON))) {
                // WSE-730 - in case of maven-dependency-plugin <3 and verbose=true, there may be cases of such lines -
                //  +- (commons-collections:commons-collections:jar:3.2.1:compile - omitted for conflict with 3.2.2)
                // in such case - remove those lines
                singleProjectLines.removeIf(line -> line.contains(Constants.DASH + Constants.WHITESPACE + Constants.OPEN_BRACKET) && line.endsWith(Character.toString(Constants.CLOSE_BRACKET)));
                // WSE-730 - removing lines without colon (which might cause an exception)
                singleProjectLines.removeIf(line -> !line.contains(Constants.COLON));
                String mvnLines = String.join(System.lineSeparator(), singleProjectLines);
                try (InputStream is = new ByteArrayInputStream(mvnLines.getBytes(StandardCharsets.UTF_8.name()));
                     Reader lineReader = new InputStreamReader(is, UTF_8)) {
                    Parser parser = InputType.TEXT.newParser();
                    Node tree = parser.parse(lineReader);
                    if (tree != null)
                        nodes.add(tree);
                } catch (UnsupportedEncodingException e) {
                    logger.warn("unsupportedEncoding error parsing output : {}", e.getMessage());
                    logger.debug("unsupportedEncoding error parsing output : {}", e.getStackTrace());
                } catch (ParseException e) {
                    logger.warn("error parsing output : {} ", e.getMessage());
                    logger.debug("error parsing output : {} ", e.getStackTrace());
                } catch (Exception e) {
                    // this can happen often - some parts of the output are not parsable
                    logger.warn("error parsing output : {}", e.getMessage());
                    logger.debug("error parsing output : {} \n{}", e.getMessage(), mvnLines);
                }
            }
        });
        return nodes;
    }

    // so : 29095967
    private static Collector<String, List<List<String>>, List<List<String>>> splitBySeparator(Predicate<String> sep) {
        return Collector.of(() -> new ArrayList<List<String>>(Arrays.asList(new ArrayList<>())),
                (l, elem) -> {
                    if (sep.test(elem)) {
                        l.add(new ArrayList<>());
                    } else l.get(l.size() - 1).add(elem);
                },
                (l1, l2) -> {
                    l1.get(l1.size() - 1).addAll(l2.remove(0));
                    l1.addAll(l2);
                    return l1;
                });
    }
}
