package org.whitesource.fs.configuration;

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
    private boolean loginSudo;

    // Amazon ECR configurations
    private List<String> amazonRegistryIds;
    private boolean remoteDockerAmazonEnabled;
    private String amazonRegion = "east";
    private int amazonMaxPullImages;

    // Azure configurations
    private boolean remoteDockerAzureEnabled;
    private String azureUserName;
    private String azureUserPassword;
    private List<String> azureRegistryNames;

    /* --- Constructors --- */

    public RemoteDockerConfiguration(List<String> imageNames, List<String> imageTags, List<String> imageDigests,
                                     boolean forceDelete, boolean remoteDockerEnabled, int maxScanImages,
                                     boolean forcePull, int maxPullImages, boolean loginSudo) {
        this.imageNames = imageNames;
        this.imageTags = imageTags;
        this.imageDigests = imageDigests;
        this.forceDelete = forceDelete;
        this.remoteDockerEnabled = remoteDockerEnabled;
        this.maxScanImages = maxScanImages;
        this.amazonMaxPullImages = 0;
        this.forcePull = forcePull;
        this.maxPullImages = maxPullImages;
        this.loginSudo = loginSudo;
    }

    /* --- Getters / Setters --- */

    public List<String> getImageNames() {
        return imageNames;
    }

    public List<String> getImageTags() {
        return imageTags;
    }

    public List<String> getImageDigests() {
        return imageDigests;
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

    public int getMaxScanImages() {
        return maxScanImages;
    }

    public boolean isForcePull() {
        return forcePull;
    }

    public int getMaxPullImages() {
        return maxPullImages;
    }

    public boolean isLoginSudo() {
        return loginSudo;
    }

    // ------------- Amazon methods -------------

    public List<String> getAmazonRegistryIds() {
        return amazonRegistryIds;
    }

    public void setAmazonRegistryIds(List<String> amazonRegistryIds) {
        this.amazonRegistryIds = amazonRegistryIds;
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

    public int getAmazonMaxPullImages() {
        return amazonMaxPullImages;
    }

    public void setAmazonMaxPullImages(int amazonMaxPullImages) {
        this.amazonMaxPullImages = amazonMaxPullImages;
    }

    // ---------- Azure Methods --------------
    public boolean isRemoteDockerAzureEnabled() {
        return remoteDockerAzureEnabled;
    }

    public void setRemoteDockerAzureEnabled(boolean remoteDockerAzureEnabled) {
        this.remoteDockerAzureEnabled = remoteDockerAzureEnabled;
    }

    public String getAzureUserName() {
        return azureUserName;
    }

    public void setAzureUserName(String azureUserName) {
        this.azureUserName = azureUserName;
    }

    public String getAzureUserPassword() {
        return azureUserPassword;
    }

    public void setAzureUserPassword(String azureUserPassword) {
        this.azureUserPassword = azureUserPassword;
    }

    public List<String> getAzureRegistryNames() {
        return azureRegistryNames;
    }

    public void setAzureRegistryNames(List<String> azureRegistryNames) {
        this.azureRegistryNames = azureRegistryNames;
    }
}
