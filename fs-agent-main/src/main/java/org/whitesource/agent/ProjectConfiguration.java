package org.whitesource.agent;

import org.whitesource.fs.configuration.AgentConfiguration;

import java.util.*;

/**
 * @author chen.luigi
 */
public class ProjectConfiguration {

    /* --- Private Members --- */

    private AgentConfiguration agentConfiguration;
    private List<String>  scannerBaseDirs;
    private Map<String, Set<String>> appPathsToDependencyDirs;
    private boolean scmConnector;


    /* --- Constructors --- */

    public ProjectConfiguration(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
        scannerBaseDirs = new LinkedList<>();
        appPathsToDependencyDirs = new HashMap<>();
        this.scmConnector = false;
    }

    public ProjectConfiguration(AgentConfiguration agentConfiguration, List<String> scannerBaseDirs, Map<String, Set<String>> appPathsToDependencyDirs, boolean scmConnector) {
        this.agentConfiguration = agentConfiguration;
        this.scannerBaseDirs = scannerBaseDirs;
        this.appPathsToDependencyDirs = appPathsToDependencyDirs;
        this.scmConnector = scmConnector;
    }

    /* --- Getters / Setters --- */

    public AgentConfiguration getAgentConfiguration() {
        return agentConfiguration;
    }

    public void setAgentConfiguration(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    public List<String> getScannerBaseDirs() {
        return scannerBaseDirs;
    }

    public void setScannerBaseDirs(List<String> scannerBaseDirs) {
        this.scannerBaseDirs = scannerBaseDirs;
    }

    public Map<String, Set<String>> getAppPathsToDependencyDirs() {
        return appPathsToDependencyDirs;
    }

    public void setAppPathsToDependencyDirs(Map<String, Set<String>> appPathsToDependencyDirs) {
        this.appPathsToDependencyDirs = appPathsToDependencyDirs;
    }

    public boolean isScmConnector() {
        return scmConnector;
    }

    public void setScmConnector(boolean scmConnector) {
        this.scmConnector = scmConnector;
    }
}
