package org.whitesource.agent.dependency.resolver.docker.remotedocker;

import java.util.Date;
import java.util.List;
import java.util.Objects;

public class DockerImageAmazon extends AbstractRemoteDockerImage {

    private String registryId;
    private String mainTag;

    public DockerImageAmazon() {
    }

    public DockerImageAmazon(String registryId, String repositoryName, String imageDigest, List<String> imageTags,
                             Date imagePushedAt, String imageManifest, String mainTag, String imageSha256) {
        super(repositoryName, imageDigest, imageTags, imagePushedAt, imageManifest, imageSha256);
        this.registryId = registryId;
        this.mainTag = mainTag;
    }

    public String getRegistryId() {
        return registryId;
    }

    public void setRegistryId(String registryId) {
        this.registryId = registryId;
    }

    public String getMainTag() {
        return mainTag;
    }

    public void setMainTag(String mainTag) {
        this.mainTag = mainTag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DockerImageAmazon that = (DockerImageAmazon) o;
        return Objects.equals(registryId, that.registryId) &&
                Objects.equals(mainTag, that.mainTag);
    }

    @Override
    public int hashCode() {

        return Objects.hash(registryId, mainTag);
    }

    @Override
    public String toString() {
        return "DockerImageAmazon{" +
                "registryId='" + registryId + '\'' +
                ", mainTag='" + mainTag + '\'' +
                ", repositoryName='" + repositoryName + '\'' +
                ", imageDigest='" + imageDigest + '\'' +
                ", imageTags=" + imageTags +
                ", imagePushedAt=" + imagePushedAt +
                ", imageManifest='" + imageManifest + '\'' +
                '}';
    }
}
