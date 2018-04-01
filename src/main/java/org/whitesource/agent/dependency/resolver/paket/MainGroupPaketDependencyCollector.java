package org.whitesource.agent.dependency.resolver.paket;

import java.util.List;

/**
 * @author raz.nitzan
 */
public class MainGroupPaketDependencyCollector extends AbstractPaketDependencyCollector {

    /* --- Statics Members --- */

    private static final String MAIN = "Main";
    private static final String EMPTY_STRING = "";
    private static final String FORWARD_SLASH = "/";

    /* --- Constructors --- */

    public MainGroupPaketDependencyCollector(List<String> linesOfDirectDependencies, String[] paketIgnoredScopes) {
        super(linesOfDirectDependencies, paketIgnoredScopes);
    }

    /* --- Override protected methods --- */

    @Override
    protected String getGroupName() {
        return MAIN;
    }

    @Override
    protected String beginGroupLine() {
        return EMPTY_STRING;
    }

    @Override
    protected String getFolderPathOfDependency(String dependencyName) {
        return getPackagesFolder() + FORWARD_SLASH + dependencyName;
    }
}
