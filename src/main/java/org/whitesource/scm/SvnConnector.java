package org.whitesource.scm;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * Connector for SVN repositories.
 *
 * @author tom.shapira
 */
public class SvnConnector extends ScmConnector {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(SvnConnector.class);

    private static final String URL_BRANCHES = "/branches/";
    private static final String URL_TAGS = "/tags/";
    private static final String URL_TRUNK = "/trunk";
    private static final String TRUNK = "trunk";

    /* --- Constructors --- */

    public SvnConnector(String username, String password, String url, String branch, String tag) {
        super(username, password, url, branch, tag, null);
    }

    /* --- Overridden methods --- */

    @Override
    protected File cloneRepository(File dest) {
        // build url
        String url = getUrl();
        String branch = getBranch();
        String tag = getTag();
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(url);
        if (StringUtils.isNotBlank(branch)) {
            if (branch.equalsIgnoreCase(TRUNK)) {
                if (!url.endsWith(TRUNK)) {
                    urlBuilder.append(URL_TRUNK);
                }
            } else {
                urlBuilder.append(URL_BRANCHES);
                urlBuilder.append(branch);
            }
        } else if (StringUtils.isNotBlank(tag)) {
            urlBuilder.append(URL_TAGS);
            urlBuilder.append(tag);
        }

        // setup svn client
        SVNClientManager clientManager = SVNClientManager.newInstance(SVNWCUtil.createDefaultOptions(true), getUsername(), getPassword());
        SVNUpdateClient updateClient = clientManager.getUpdateClient();
        updateClient.setIgnoreExternals(false);

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        try {
            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSingleTarget(SvnTarget.fromFile(dest));
            checkout.setSource(SvnTarget.fromURL(SVNURL.parseURIEncoded(urlBuilder.toString())));
            checkout.setRevision(SVNRevision.HEAD);
            checkout.run();
        } catch (SVNException e) {
            logger.error("error during checkout: {}", e.getMessage());
        } finally {
            svnOperationFactory.dispose();
        }

        return dest;
    }

    @Override
    public ScmType getType() {
        return ScmType.SVN;
    }
}
