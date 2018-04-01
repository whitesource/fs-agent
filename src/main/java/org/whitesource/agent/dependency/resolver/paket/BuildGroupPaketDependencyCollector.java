package org.whitesource.agent.dependency.resolver.paket;

import java.util.List;

/**
 * @author raz.nitzan
 */
public class BuildGroupPaketDependencyCollector extends AbstractPaketDependencyCollector {

    /* --- Statics Members --- */

    private static final String GROUP = "GROUP";
    private static final String BUILD = "Build";
    private static final String FORWARD_SLASH = "/";
    private static final String SPACE = " ";

    /* --- Constructors --- */

    public BuildGroupPaketDependencyCollector(List<String> linesOfDirectDependencies, String[] paketIgnoredScopes) {
        super(linesOfDirectDependencies, paketIgnoredScopes);
    }

    /* --- Override protected methods --- */

    @Override
    protected String getGroupName() {
        return BUILD;
    }

    @Override
    protected String beginGroupLine() {
        return GROUP + SPACE + BUILD;
    }

    @Override
    protected String getFolderPathOfDependency(String dependencyName) {
        return getPackagesFolder() + FORWARD_SLASH + BUILD + FORWARD_SLASH + dependencyName;
    }
}
