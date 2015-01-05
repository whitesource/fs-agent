package org.whitesource.scm;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.InvalidPathException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Connector for Git repositories.
 *
 * @author tom.shapira
 */
public class GitConnector extends ScmConnector {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(GitConnector.class);

    /* --- Constructors --- */

    public GitConnector(String username, String password, String url, String branch, String tag) {
        super(username, password, url, branch, tag);
    }

    /* --- Overridden methods --- */

    @Override
    protected File cloneRepository(File dest) {
        Git git = null;
        try {
            // set branch name
            String branchName = MASTER;;
            String branch = getBranch();
            if (StringUtils.isNotBlank(branch)) {
                branchName = branch;
            }
            String tag = getTag();
            if (StringUtils.isNotBlank(tag)) {
                branchName = tag;
            }

            // clone repository
            git = Git.cloneRepository()
                    .setURI(getUrl())
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(getUsername(), getPassword()))
                    .setBranch(branchName)
                    .setDirectory(dest)
                    .call();
        } catch (InvalidPathException e) {
            logger.warn("Error cloning git repository: {}", e.getMessage());
        } catch (GitAPIException e) {
            logger.warn("Error processing git repository: {}", e.getMessage());
        } finally {
            if (git != null) {
                git.close();
            }
        }
        return dest;
    }

    @Override
    public ScmType getType() {
        return ScmType.GIT;
    }
}
