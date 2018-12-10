package org.whitesource.agent.dependency.resolver.docker.remotedocker;

import org.whitesource.agent.dependency.resolver.docker.DockerImage;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public abstract class AbstractRemoteDockerImage {

    protected String repositoryName;
    protected String imageDigest;
    protected String imageSha256;
    protected List<String> imageTags;
    protected Date imagePushedAt;
    // Contains SHA256 similar to Docker's SHA256
    protected String imageManifest;

    public AbstractRemoteDockerImage() {
    }

    public AbstractRemoteDockerImage(String repositoryName, String imageDigest, List<String> imageTags,
                                     Date imagePushedAt, String imageManifest, String imageSha256) {
        this.repositoryName = repositoryName;
        this.imageDigest = imageDigest;
        this.imageTags = imageTags;
        this.imagePushedAt = imagePushedAt;
        this.imageManifest = imageManifest;
        this.imageSha256 = imageSha256;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getImageDigest() {
        return imageDigest;
    }

    public String getImageSha256() {
        return imageSha256;
    }

    public void setImageSha256(String imageSha256) {
        this.imageSha256 = imageSha256;
    }

    public void setImageDigest(String imageDigest) {
        this.imageDigest = imageDigest;
    }

    public List<String> getImageTags() {
        return imageTags;
    }

    public void setImageTags(List<String> imageTags) {
        this.imageTags = imageTags;
    }

    public DockerImage getDockerImage(String tag) {
        return new DockerImage(repositoryName, tag, imageDigest);
    }

    public List<DockerImage> getAllDockerImages() {
        if (imageTags == null || imageTags.isEmpty()) {
            return Collections.emptyList();
        }
        List<DockerImage> result = new LinkedList<>();
        for(String tag : imageTags) {
            result.add(getDockerImage(tag));
        }
        return result;
    }

    /* --- Abstract methods --- */

    public abstract String getUniqueIdentifier();
}
