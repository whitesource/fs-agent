package org.whitesource.fs.configuration;

import java.util.ArrayList;
import java.util.List;

public class RemoteDockerConfiguration {

    private List<String> imageNames;
    private List<String> imageTags;
    private List<String> imageDigests;

    private List<String> amazonRegistryIds;
    private String amazonRegion = "east";


    public RemoteDockerConfiguration() {
        this.imageNames     = new ArrayList<>();
        this.imageTags      = new ArrayList<>();
        this.imageDigests   = new ArrayList<>();
    }

    public RemoteDockerConfiguration(List<String> imageNames, List<String> imageTags, List<String> imageDigests) {
        this.imageNames = imageNames;
        this.imageTags = imageTags;
        this.imageDigests = imageDigests;
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

    public List<String> getAmazonRegistryId() {
        return amazonRegistryIds;
    }

    public void setAmazonRegistryId(List<String> amazonRegistryIds) {
        this.amazonRegistryIds = amazonRegistryIds;
    }

    public String getAmazonRegion() {
        return amazonRegion;
    }

    public void setAmazonRegion(String amazonRegion) {
        this.amazonRegion = amazonRegion;
    }
}
