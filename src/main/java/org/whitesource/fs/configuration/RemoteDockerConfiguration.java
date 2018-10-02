package org.whitesource.fs.configuration;

import java.util.LinkedList;
import java.util.List;

public class RemoteDockerConfiguration {

    // Global Docker configurations
    private List<String> imageNames;
    private List<String> imageTags;
    private List<String> imageDigests;
    private boolean forceDelete;
    private boolean remoteDockerEnabled;
    private int maxScanImages;
    private boolean forcePull;
    private int maxPullImages;

    // Amazon ECR configurations
    private List<String> amazonRegistryIds;
    private boolean remoteDockerAmazonEnabled;
    private String amazonRegion = "east";
    private int amazonMaxPullImages;


    public RemoteDockerConfiguration() {
        this.imageNames     = new LinkedList<>();
        this.imageTags      = new LinkedList<>();
        this.imageDigests   = new LinkedList<>();
        forceDelete         = false;
        remoteDockerEnabled = false;
        maxScanImages       = 0;
        amazonMaxPullImages = 0;
        forcePull           = false;
        maxPullImages       = 3;
    }

    public RemoteDockerConfiguration(List<String> imageNames, List<String> imageTags, List<String> imageDigests,
                                     boolean forceDelete, boolean remoteDockerEnabled, int maxScanImages,
                                     boolean forcePull, int maxPullImages) {
        this.imageNames = imageNames;
        this.imageTags = imageTags;
        this.imageDigests = imageDigests;
        this.forceDelete = forceDelete;
        this.remoteDockerEnabled = remoteDockerEnabled;
        this.maxScanImages = maxScanImages;
        this.amazonMaxPullImages = 0;
        this.forcePull = forcePull;
        this.maxPullImages = maxPullImages;
    }

    public List<String> getImageNames() {
        return imageNames;
    }
/*
    public void setImageNames(List<String> imageNames) {
        this.imageNames = imageNames;
    }
*/
    public List<String> getImageTags() {
        return imageTags;
    }
/*
    public void setImageTags(List<String> imageTags) {
        this.imageTags = imageTags;
    }
*/
    public List<String> getImageDigests() {
        return imageDigests;
    }
/*
    public void setImageDigests(List<String> imageDigests) {
        this.imageDigests = imageDigests;
    }
*/
    public boolean isForceDelete() {
        return forceDelete;
    }

    public void setForceDelete(boolean forceDelete) {
        this.forceDelete = forceDelete;
    }

    public boolean isRemoteDockerEnabled() {
        return remoteDockerEnabled;
    }
/*
    public void setRemoteDockerEnabled(boolean remoteDockerEnabled) {
        this.remoteDockerEnabled = remoteDockerEnabled;
    }
*/
    public int getMaxScanImages() {
        return maxScanImages;
    }
/*
    public void setMaxScanImages(int maxScanImages) {
        this.maxScanImages = maxScanImages;
    }
*/
    // Amazon methods

    public List<String> getAmazonRegistryIds() {
        return amazonRegistryIds;
    }

    public void setAmazonRegistryIds(List<String> amazonRegistryIds) {
        this.amazonRegistryIds = amazonRegistryIds;
    }
/*
    public String getAmazonRegion() {
        return amazonRegion;
    }
*/
    public void setAmazonRegion(String amazonRegion) {
        this.amazonRegion = amazonRegion;
    }

    public boolean isRemoteDockerAmazonEnabled() {
        return remoteDockerAmazonEnabled;
    }

    public void setRemoteDockerAmazonEnabled(boolean remoteDockerAmazonEnabled) {
        this.remoteDockerAmazonEnabled = remoteDockerAmazonEnabled;
    }

    public int getAmazonMaxPullImages() {
        return amazonMaxPullImages;
    }

    public void setAmazonMaxPullImages(int amazonMaxPullImages) {
        this.amazonMaxPullImages = amazonMaxPullImages;
    }

    public boolean isForcePull() {
        return forcePull;
    }

    public void setForcePull(boolean forcePull) {
        this.forcePull = forcePull;
    }

    public int getMaxPullImages() {
        return maxPullImages;
    }

    public void setMaxPullImages(int maxPullImages) {
        this.maxPullImages = maxPullImages;
    }
}
