package org.whitesource.fs.configuration;

import java.util.ArrayList;
import java.util.List;

public class RemoteDockerConfiguration {

    // Global Docker configurations
    private List<String> imageNames;
    private List<String> imageTags;
    private List<String> imageDigests;
    private boolean forceDelete;
    private boolean remoteDockerEnabled;

    // Amazon ECR configurations
    private List<String> amazonRegistryIds;
    private boolean remoteDockerAmazonEnabled;
    private String amazonRegion = "east";


    public RemoteDockerConfiguration() {
        this.imageNames     = new ArrayList<>();
        this.imageTags      = new ArrayList<>();
        this.imageDigests   = new ArrayList<>();
        forceDelete         = false;
        remoteDockerEnabled = false;
    }

    public RemoteDockerConfiguration(List<String> imageNames, List<String> imageTags, List<String> imageDigests,
                                     boolean forceDelete, boolean remoteDockerEnabled) {
        this.imageNames = imageNames;
        this.imageTags = imageTags;
        this.imageDigests = imageDigests;
        this.forceDelete = forceDelete;
        this.remoteDockerEnabled = remoteDockerEnabled;
    }

    public List<String> getImageNames() {
        return imageNames;
    }

    public void setImageNames(List<String> imageNames) {
        this.imageNames = imageNames;
    }

    public List<String> getImageTags() {
        return imageTags;
    }

    public void setImageTags(List<String> imageTags) {
        this.imageTags = imageTags;
    }

    public List<String> getImageDigests() {
        return imageDigests;
    }

    public void setImageDigests(List<String> imageDigests) {
        this.imageDigests = imageDigests;
    }

    public boolean isForceDelete() {
        return forceDelete;
    }

    public void setForceDelete(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }

    public boolean isRemoteDockerEnabled() {
        return remoteDockerEnabled;
    }

    public void setRemoteDockerEnabled(boolean remoteDockerEnabled) {
        this.remoteDockerEnabled = remoteDockerEnabled;
    }

    // Amazon methods

    public List<String> getAmazonRegistryIds() {
        return amazonRegistryIds;
    }

    public void setAmazonRegistryIds(List<String> amazonRegistryIds) {
        this.amazonRegistryIds = amazonRegistryIds;
    }

    public String getAmazonRegion() {
        return amazonRegion;
    }

    public void setAmazonRegion(String amazonRegion) {
        this.amazonRegion = amazonRegion;
    }

    public boolean isRemoteDockerAmazonEnabled() {
        return remoteDockerAmazonEnabled;
    }

    public void setRemoteDockerAmazonEnabled(boolean remoteDockerAmazonEnabled) {
        this.remoteDockerAmazonEnabled = remoteDockerAmazonEnabled;
    }
}
