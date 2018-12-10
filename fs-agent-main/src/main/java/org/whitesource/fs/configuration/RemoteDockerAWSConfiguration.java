/*
package org.whitesource.fs.configuration;

import java.util.List;

public class RemoteDockerAWSConfiguration extends RemoteDockerConfiguration {

    private String registryId;
    private String region = "east";

    public RemoteDockerAWSConfiguration() {
        super();
    }

    public RemoteDockerAWSConfiguration(String registryId, String region, List<String> repositoryNames, List<String> imageTags, List<String> imageDigests) {
        super(repositoryNames, imageTags, imageDigests);
        this.registryId = registryId;
        this.region = region;
    }

    public String getRegistryId() {
        return registryId;
    }

    public void setRegistryId(String registryId) {
        this.registryId = registryId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public List<String> getRepositoryNames() {
        return super.getImageNames();
    }

}
*/