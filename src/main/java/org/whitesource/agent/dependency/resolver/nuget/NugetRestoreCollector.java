package org.whitesource.agent.dependency.resolver.nuget;

import org.whitesource.agent.TempFolders;
import org.whitesource.agent.dependency.resolver.dotNet.RestoreCollector;

import java.nio.file.Paths;

/**
 * @author raz.nitzan
 */

public class NugetRestoreCollector extends RestoreCollector {

    /* --- Statics Members --- */

    private static final String NUGET_RESTORE_TMP_DIRECTORY = Paths.get(System.getProperty("java.io.tmpdir"), TempFolders.UNIQUE_DOTNET_TEMP_FOLDER).toString();
    private static final String NUGET_COMMAND = "nuget";
    private static final String PACKAGES_DIRECTORY = "-PackagesDirectory";

    /* --- Constructors --- */

    public NugetRestoreCollector() {
        super(NUGET_RESTORE_TMP_DIRECTORY, NUGET_COMMAND);
    }

    @Override
    protected String[] getInstallParams(String pathToDownloadPackages, String csprojFile) {
        return new String[]{NUGET_COMMAND, RESTORE, csprojFile, PACKAGES_DIRECTORY, pathToDownloadPackages};
    }
}
