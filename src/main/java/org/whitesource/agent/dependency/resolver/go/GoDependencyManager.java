package org.whitesource.agent.dependency.resolver.go;

public enum GoDependencyManager {
    DEP("dep"),
    GO_DEP("godep");


    private final String type;

    GoDependencyManager(String type){
        this.type = type;
    }

    public String getType(){
        return this.type;
    }

    public static GoDependencyManager getFromType(String type){
        for (GoDependencyManager goDependencyManager : GoDependencyManager.values()){
            if (goDependencyManager.getType().equals(type))
                return goDependencyManager;
        }
        return null;
    }
}
