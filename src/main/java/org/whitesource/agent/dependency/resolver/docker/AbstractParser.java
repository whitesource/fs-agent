package org.whitesource.agent.dependency.resolver.docker;

import org.whitesource.agent.api.model.DependencyInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author chen.luigi
 */
public abstract class AbstractParser {

    /* --- Constructors --- */

    public AbstractParser() {
    }

    /* --- Public methods --- */

    public File findFile(File dir, String filename) {
        try (Stream<Path> paths = Files.walk(Paths.get(dir.getAbsolutePath()))) {
            String joined = paths
                    .map(String::valueOf)
                    .filter(path -> path.endsWith(filename))
                    .sorted().collect(Collectors.joining());
            return new File(joined);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public abstract Collection<DependencyInfo> parse(File file);

}
