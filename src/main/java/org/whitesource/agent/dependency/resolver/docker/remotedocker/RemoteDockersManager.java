package org.whitesource.agent.dependency.resolver.docker.remotedocker;

import org.whitesource.agent.dependency.resolver.docker.remotedocker.amazon.RemoteDockerAmazonECR;
import org.whitesource.agent.dependency.resolver.docker.remotedocker.azure.AzureRemoteDocker;
import org.whitesource.fs.configuration.RemoteDockerConfiguration;

import java.util.*;

public class RemoteDockersManager {

     private boolean remoteDockersEnabled = false;
     private List<AbstractRemoteDocker> remoteDockersList = new LinkedList<>();
     private Set<AbstractRemoteDockerImage> pulledDockerImages = new HashSet<>();

     public RemoteDockersManager(RemoteDockerConfiguration config) {
        if (config != null) {
            remoteDockersEnabled = config.isRemoteDockerEnabled();
            // TODO: Remote Docker pulling should be enable only if docker.scanImages==true && docker.pull.enable==true
            if (remoteDockersEnabled) {
                if (config.isRemoteDockerAmazonEnabled()) {
                    remoteDockersList.add(new RemoteDockerAmazonECR(config));
                }
                if(config.isRemoteDockerAzureEnabled()) {
                    remoteDockersList.add(new AzureRemoteDocker(config));
                }
            }
        }
    }

    public Set<AbstractRemoteDockerImage> pullRemoteDockerImages() {
        if (!remoteDockersEnabled) {
            return Collections.emptySet();
        }
        for (AbstractRemoteDocker remoteDocker : remoteDockersList) {
            Set<AbstractRemoteDockerImage> pulledImages = remoteDocker.pullRemoteDockerImages();
            if (pulledImages != null) {
                pulledDockerImages.addAll(pulledImages);
            }
        }
        return pulledDockerImages;
    }

    public void removePulledRemoteDockerImages() {
        if (!remoteDockersEnabled) {
            return;
        }
        for (AbstractRemoteDocker remoteDocker : remoteDockersList) {
            remoteDocker.removePulledRemoteDockerImages();
        }
        pulledDockerImages.clear();
    }

    public Set<AbstractRemoteDockerImage> getPulledDockerImages() {
        return pulledDockerImages;
    }
}
