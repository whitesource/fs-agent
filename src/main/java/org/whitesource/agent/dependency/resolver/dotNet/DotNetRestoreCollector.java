package org.whitesource.agent.dependency.resolver.dotNet;

import java.nio.file.Paths;

/**
 * @author raz.nitzan
 */
public class DotNetRestoreCollector extends RestoreCollector {

    /* --- Statics Members --- */

    private static final String DOTNET_RESTORE_TMP_DIRECTORY = Paths.get(System.getProperty("java.io.tmpdir"), "WhiteSource-DotnetRestore").toString();
    private static final String DOTNET_COMMAND = "dotnet";
    private static final String PACKAGES = "--packages";

    /* --- Constructors --- */

    public DotNetRestoreCollector() {
        super(DOTNET_RESTORE_TMP_DIRECTORY, DOTNET_COMMAND);
    }

    @Override
    protected String[] getInstallParams(String pathToDownloadPackages, String csprojFile) {
        return new String[]{DOTNET_COMMAND, RESTORE, csprojFile, PACKAGES, pathToDownloadPackages};
    }
}
