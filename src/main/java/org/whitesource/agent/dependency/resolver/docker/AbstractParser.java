package org.whitesource.agent.dependency.resolver.docker;

import org.whitesource.agent.api.model.DependencyInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;

/**
 * @author chen.luigi
 */
public abstract class AbstractParser {

    /* --- Constructors --- */

    public AbstractParser() {

    }

    /* --- Public methods --- */

    static void closeStream(BufferedReader br, FileReader fr) {
        try {
            if (br != null)
                br.close();
            if (fr != null)
                fr.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /* --- Abstract methods --- */

    public abstract Collection<DependencyInfo> parse(File file);

    public abstract File findFile(String[] files, String filename);

}
