package org.whitesource.agent.dependency.resolver.sbt;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.List;

@Root(strict=false)
public class IvyReport {
    @ElementList
    private List<Module> dependencies;

    public List<Module> getDependencies() {
        return dependencies;
    }
}

class Module {
    @Attribute
    private String organisation;

    @Attribute
    private String name;

    @ElementList(inline = true)
    private List<Revision> revisionsList;

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

    public String getVersion() {
        return name;
    }

    public boolean getEvicted(){
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
    private String rev;

    public String getGroupId() {
        return organisation;
    }

    public String getArtifactId() {
        return name;
    }

    public String getVersion() {
        return rev;
    }
}

@Root(strict=false)
class Artifact{
    @Attribute
    private String location;

    public String getPathToJar() {
        return location;
    }
}