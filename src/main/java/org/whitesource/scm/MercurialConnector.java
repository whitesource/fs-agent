package org.whitesource.scm;

import com.aragost.javahg.BaseRepository;
import com.aragost.javahg.Repository;
import com.aragost.javahg.RepositoryConfiguration;
import com.aragost.javahg.commands.BranchCommand;
import org.apache.commons.lang.StringUtils;

import java.io.File;

/**
 * Connector for Mercurial (hg) repositories.
 *
 * @author tom.shapira
 */
public class MercurialConnector extends ScmConnector {

    /* --- Constructors --- */

    public MercurialConnector(String username, String password, String url, String branch, String tag) {
        super(username, password, url, branch, tag, null);
    }

    /* --- Overridden methods --- */

    @Override
    protected File cloneRepository(File dest) {
        BaseRepository repo = Repository.clone(RepositoryConfiguration.DEFAULT, dest, getUrl());

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
        BranchCommand.on(repo).set(branchName);
        return dest;
    }

    @Override
    public ScmType getType() {
        return ScmType.MERCURIAL;
    }
}
