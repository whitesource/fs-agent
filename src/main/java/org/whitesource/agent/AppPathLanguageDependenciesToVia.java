package org.whitesource.agent;

import org.whitesource.agent.api.model.DependencyInfo;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author raz.nitzan
 */
public class AppPathLanguageDependenciesToVia {

    /* --- Members --- */

    private String appPath;
    private LanguageForVia language;
    private Collection<DependencyInfo> dependencies = new ArrayList<>();

    /* --- Constructor --- */

    public AppPathLanguageDependenciesToVia(String appPath, LanguageForVia language) {
        this.appPath = appPath;
        this.language = language;
    }

    public String getAppPath() {
        return this.appPath;
    }

    public LanguageForVia getLanguage() {
        return this.language;
    }

    public Collection<DependencyInfo> getDependencies() {
        return dependencies;
    }
}
