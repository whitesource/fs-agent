package org.whitesource.scm;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.TempFolders;
import org.whitesource.agent.utils.FilesUtils;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;

/**
 * This class holds all components for connecting to repositories using git/svm/mercurial protocol.
 *
 * @author tom.shapira
 */
public abstract class ScmConnector {


    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(ScmConnector.class);

    public static final String MASTER = "master";

    /* --- Members --- */

    private final String username;
    private final String password;
    private final String url;
    private final String branch;
    private final String tag;
    private final String privateKey;
    private File cloneDirectory;

    /* --- Constructors --- */

    protected ScmConnector(String username, String password, String url, String branch, String tag, String privateKey) {
        this.username = username;
        this.password = password;
        this.url = url;
        this.branch = branch;
        this.tag = tag;
        this.privateKey = privateKey;
    }

    /* --- Static methods --- */

    public static ScmConnector create(String scmType, String url, String privateKey, String username, String password, String branch, String tag) {
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
                    scmConnector = new GitConnector(privateKey, username, password, url, branch, tag);
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
        String scmTempFolder = new FilesUtils().createTmpFolder(false, TempFolders.UNIQUE_SCM_TEMP_FOLDER);
        cloneDirectory = new File(scmTempFolder, getType().toString().toLowerCase() + Constants.UNDERSCORE +
                getUrlName() + Constants.UNDERSCORE + getBranch());
        FilesUtils.deleteDirectory(cloneDirectory); // delete just in case it's not empty

        logger.info("Cloning repository {} ...this may take a few minutes", getUrl());
        File branchDirectory = cloneRepository(cloneDirectory);
        return branchDirectory;
    }

    public void deleteCloneDirectory() {
        new TempFolders().deleteTempFoldersHelper(Paths.get(System.getProperty("java.io.tmpdir"), TempFolders.UNIQUE_SCM_TEMP_FOLDER).toString());
    }

    /* --- Abstract methods --- */

    protected abstract File cloneRepository(File dest);

    public abstract ScmType getType();

    /* --- Private methods --- */

//    private void deleteDirectory(File directory) {
//        if (directory != null) {
//            try {
//                FileUtils.forceDelete(directory);
//            } catch (IOException e) {
//                // do nothing
//            }
//        }
//    }

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

    public String getUrlName() {
       return this.url.substring(this.url.lastIndexOf('/') + 1, this.url.length());
    }

    public String getBranch() {
        return branch;
    }

    public String getTag() {
        return tag;
    }

    public String getPrivateKey() {
        return privateKey;
    }
}
