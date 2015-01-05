package org.whitesource.scm;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * This class holds all components for connecting to repositories using git/svm/mercurial protocol.
 *
 * @author tom.shapira
 */
public abstract class ScmConnector {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ScmConnector.class);

    private static final String SCM_CONNECTOR_TMP_DIRECTORY = System.getProperty("java.io.tmpdir") + File.separator + "WhiteSource-ScmConnector";

    public static final String MASTER = "master";

    /* --- Members --- */

    private final String username;
    private final String password;
    private final String url;
    private final String branch;
    private final String tag;
    private File cloneDirectory;

    /* --- Constructors --- */

    public ScmConnector(String username, String password, String url, String branch, String tag) {
        this.username = username;
        this.password = password;
        this.url = url;
        this.branch = branch;
        this.tag = tag;
    }

    /* --- Static methods --- */

    public static ScmConnector create(String scmType, String url, String username, String password, String branch, String tag) {
        ScmConnector scmConnector = null;
        if (StringUtils.isNotBlank(scmType)) {
            ScmType type = ScmType.getValue(scmType);
            if (type == null) {
                throw new IllegalArgumentException("Invalid scm type, please select git / svn / mercurial");
            }

            if (StringUtils.isBlank(url)) {
                throw new IllegalArgumentException("No scm link provided");
            }

            switch (type) {
                case GIT:
                    scmConnector = new GitConnector(username, password, url, branch, tag);
                    break;
                case SVN:
                    scmConnector = new SvnConnector(username, password, url, branch, tag);
                    break;
                case MERCURIAL:
                    scmConnector = new MercurialConnector(username, password, url, branch, tag);
                    break;
                default: throw new IllegalArgumentException("Unsupported scm type");
            }
        }
        return scmConnector;
    }

    /* --- Public methods --- */

    /**
     * Clones the given repository.
     *
     * @return The folder in which the specific branch/tag resides.
     */
    public File cloneRepository() {
        cloneDirectory = new File(SCM_CONNECTOR_TMP_DIRECTORY, getType().toString().toLowerCase());
        deleteDirectory(cloneDirectory); // delete just in case it's not empty

        logger.info("Cloning repository {} ...this may take a few minutes", getUrl());
        File branchDirectory = cloneRepository(cloneDirectory);
        return branchDirectory;
    }

    public void deleteCloneDirectory() {
        deleteDirectory(cloneDirectory);
    }

    /* --- Abstract methods --- */

    protected abstract File cloneRepository(File dest);

    public abstract ScmType getType();

    /* --- Private methods --- */

    private void deleteDirectory(File directory) {
        if (directory != null) {
            try {
                FileUtils.forceDelete(directory);
            } catch (IOException e) {
                // do nothing
            }
        }
    }

    /* --- Getters / Setters --- */

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }

    public String getBranch() {
        return branch;
    }

    public String getTag() {
        return tag;
    }
}
