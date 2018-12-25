package org.whitesource.agent.dependency.resolver.docker.remotedocker.azure;

import org.whitesource.agent.Constants;
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
        // TODO should be only this.getRegistry() + Constants.FORWARD_SLASH + this.getRepositoryName()  , tag should be added outside
        return this.getRegistry() + Constants.FORWARD_SLASH + this.getRepositoryName() + Constants.COLON + this.getImageTags().get(0);
    }
}