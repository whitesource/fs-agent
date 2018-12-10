package org.whitesource.agent.dependency.resolver.docker.remotedocker.azure;

import org.whitesource.utils.Constants;
import org.whitesource.agent.dependency.resolver.docker.remotedocker.AbstractRemoteDockerImage;

public class AzureDockerImage extends AbstractRemoteDockerImage {

    /* --- Private fields --- */

    private String registry;

    /* --- Constructors --- */

    AzureDockerImage() {
    }

    /* --- Getters / Setters --- */

    public String getRegistry() {
        return registry;
    }

    public void setRegistry(String registry) {
        this.registry = registry;
    }

    /* --- Public methods --- */

    public String getUniqueIdentifier() {
        return this.getRegistry() + Constants.FORWARD_SLASH + getRepositoryName() + Constants.AT + getImageDigest();
    }
}