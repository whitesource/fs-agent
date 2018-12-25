package org.whitesource.agent.dependency.resolver.docker.remotedocker.azure;

import org.whitesource.agent.Constants;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.utils.Cli;

public class AzureCli extends Cli {

    /* --- Static members --- */

    private static final String AZ                          = "az";
    private static final String LOGIN                       = AZ + Constants.WHITESPACE + "login";
    private static final String LOGOUT                      = AZ + Constants.WHITESPACE + "logout";
    private static final String CONTAINER_REGISTRY          = AZ + Constants.WHITESPACE + "acr";
    private static final String ACCOUNT_LIST                = AZ + Constants.WHITESPACE + "account" + Constants.WHITESPACE + "list";
    private static final String LOGIN_CONTAINER_REGISTRY    = CONTAINER_REGISTRY + Constants.WHITESPACE + "login";
    private static final String REPOSITORY                  = CONTAINER_REGISTRY + Constants.WHITESPACE + "repository";
    private static final String REPOSITORY_LIST             = REPOSITORY + Constants.WHITESPACE + "list";
    private static final String REPOSITORY_SHOW_MANIFEST    = REPOSITORY + Constants.WHITESPACE + "show-manifests";

    private static final String USER_NAME_PARAM                 = "-u";
    private static final String USER_PASSWORD_PARAM             = "-p";
    private static final String ACR_NAME_PARAM                  = "-n";
    private static final String USER_NAME_FULL_PARAM            = "--username";
    private static final String VERSION_PARAM                   = "--version";
    private static final String REPOSITORY_NAME_PARAM           = "--repository";
    private static final String REPOSITORY_MANIFEST_ORDER_DESC  = "--orderby time_desc";

    /* --- Public methods --- */

    public String getBasicCommand() {
        return getCommandPrefix() + AZ + Constants.WHITESPACE + VERSION_PARAM;
    }

    public String getLoggedInAccountList() { return getCommandPrefix() + ACCOUNT_LIST; }

    public String getLoginCommand(String userName, String password) {
        return getCommandPrefix() + LOGIN + Constants.WHITESPACE + USER_NAME_PARAM + Constants.WHITESPACE + userName + Constants.WHITESPACE +
                USER_PASSWORD_PARAM + Constants.WHITESPACE + password;
    }

    public String getLogoutCommand(String userName) {
        return getCommandPrefix() + LOGOUT + Constants.WHITESPACE + USER_NAME_FULL_PARAM + Constants.WHITESPACE + userName;
    }

    public String getLoginContainerRegistryCommand(String acrName) {
        return getCommandPrefix() + LOGIN_CONTAINER_REGISTRY + Constants.WHITESPACE + ACR_NAME_PARAM + Constants.WHITESPACE + acrName;
    }

    public String getRepositoryListCommand(String acrName) {
        return getCommandPrefix() + REPOSITORY_LIST + Constants.WHITESPACE + ACR_NAME_PARAM + Constants.WHITESPACE + acrName;
    }

    public String getRepositoryShowManifest(String acrName, String repositoryName) {
        return getCommandPrefix() + REPOSITORY_SHOW_MANIFEST + Constants.WHITESPACE + ACR_NAME_PARAM + Constants.WHITESPACE + acrName
                + Constants.WHITESPACE + REPOSITORY_NAME_PARAM + Constants.WHITESPACE + repositoryName
                + Constants.WHITESPACE + REPOSITORY_MANIFEST_ORDER_DESC;
    }

    private String getCommandPrefix() {
        return DependencyCollector.isWindows() ? Constants.CMD + Constants.WHITESPACE
                + DependencyCollector.C_CHAR_WINDOWS + Constants.WHITESPACE : Constants.EMPTY_STRING;
    }
}