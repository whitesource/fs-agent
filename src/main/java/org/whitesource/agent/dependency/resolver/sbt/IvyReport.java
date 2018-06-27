package org.whitesource.agent.dependency.resolver.sbt;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.List;

@Root(strict=false)
public class IvyReport {

    @Element
    private Info info;

    @ElementList
    private List<Module> dependencies;

    /* --- Getters --- */

    public List<Module> getDependencies() {
        return dependencies;
    }

    public Info getInfo() {
        return info;
    }
}

@Root(strict = false)
class Info {

    @Attribute
    private String organisation;

    @Attribute
    private String module;

    @Attribute
    private String revision;

    /* --- Getters --- */

    public String getGroupId() {
        return organisation;
    }

    public String getArtifactId() {
        return module;
    }

    public String getVersion() {
        return revision;
    }
}

class Module {

    @Attribute
    private String organisation;

    @Attribute
    private String name;

    @ElementList(inline = true)
    private List<Revision> revisionsList;

    /* --- Getters --- */

    public String getGroupId() {
        return organisation;
    }

    public String getArtifactId() {
        return name;
    }

    public List<Revision> getRevisions() {
        return revisionsList;
    }
}

@Root(strict=false)
class Revision{

    @Attribute
    private String name;

    @Attribute
    private int position;

    @ElementList(inline = true)
    private List<Caller> callerList;

    @ElementList
    private List<Artifact> artifacts;

    /* --- Getters --- */

    public String getVersion() {
        return name;
    }

    // dependencies with multiple versions, only the latest is used.  the others have property 'position=-1"
    public boolean isIgnored(){
        return position == -1;
    }

    public List<Caller> getParentsList() {
        return callerList;
    }

    public List<Artifact> getArtifacts() {
        return artifacts;
    }
}

@Root(strict=false)
class Caller{

    @Attribute
    private String organisation;

    @Attribute
    private String name;

    @Attribute
    private String callerrev;

    /* --- Getters --- */

    public String getGroupId() {
        return organisation;
    }

    public String getArtifactId() {
        return name;
    }

    public String getVersion() {
        return callerrev;
    }
}

@Root(strict=false)
class Artifact{

    @Attribute
    private String location;

    /* --- Getters --- */

    public String getPathToJar() {
        return location;
    }
}