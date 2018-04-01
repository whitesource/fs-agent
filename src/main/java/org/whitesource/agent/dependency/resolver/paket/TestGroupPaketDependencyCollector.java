package org.whitesource.agent.dependency.resolver.paket;

import java.util.List;

/**
 * @author raz.nitzan
 */
public class TestGroupPaketDependencyCollector extends AbstractPaketDependencyCollector {

    /* --- Statics Members --- */

    private static final String GROUP = "GROUP";
    private static final String TEST = "Test";
    private static final String FORWARD_SLASH = "/";
    private static final String SPACE = " ";

    /* --- Constructors --- */

    public TestGroupPaketDependencyCollector(List<String> linesOfDirectDependencies, String[] paketIgnoredScopes) {
        super(linesOfDirectDependencies, paketIgnoredScopes);
    }

    /* --- Override protected methods --- */

    @Override
    protected String getGroupName() {
        return TEST;
    }

    @Override
    protected String beginGroupLine() {
        return GROUP + SPACE + TEST;
    }

    @Override
    protected String getFolderPathOfDependency(String dependencyName) {
        return getPackagesFolder() + FORWARD_SLASH + TEST + FORWARD_SLASH + dependencyName;
    }
}
