package org.whitesource.scm;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum for scm types.
 *
 * @author tom.shapira
 */
public enum ScmType {

    GIT,
    SVN,
    MERCURIAL;

    /* --- Static members --- */

    private static final Map<String, ScmType> scmTypeMap;

    static {
        scmTypeMap = new HashMap<String, ScmType>();
        scmTypeMap.put("git", ScmType.GIT);
        scmTypeMap.put("svn", ScmType.SVN);
        scmTypeMap.put("mercurial", ScmType.MERCURIAL);
    }

    /* --- Static methods --- */

    public static ScmType getValue(String value) {
        return scmTypeMap.get(value);
    }
}
