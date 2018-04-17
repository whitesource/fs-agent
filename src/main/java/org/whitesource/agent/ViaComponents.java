package org.whitesource.agent;

import org.whitesource.agent.api.model.DependencyInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author raz.nitzan
 */
public class ViaComponents {

    /* --- Members --- */

    private String appPath;
    private ViaLanguage language;
    private List<DependencyInfo> dependencies = new ArrayList<>();

    /* --- Constructor --- */

    public ViaComponents(String appPath, ViaLanguage language) {
        this.appPath = appPath;
        this.language = language;
    }

    public String getAppPath() {
        return this.appPath;
    }

    public ViaLanguage getLanguage() {
        return this.language;
    }

    public List<DependencyInfo> getDependencies() {
        return dependencies;
    }
}
