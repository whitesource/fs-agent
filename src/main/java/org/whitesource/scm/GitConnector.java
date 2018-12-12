package org.whitesource.scm;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.InvalidPathException;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.whitesource.agent.utils.LoggerFactory;

import java.io.File;

/**
 * Connector for Git repositories.
 *
 * @author tom.shapira
 */
public class GitConnector extends ScmConnector {

    /* --- Static members --- */

    private Logger logger = LoggerFactory.getLogger(GitConnector.class);

    /* --- Constructors --- */

    public GitConnector(String privateKey, String username, String password, String url, String branch, String tag) {
        super(username, password, url, branch, tag, privateKey);
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

            CloneCommand cloneCommand = Git.cloneRepository();

            // use private key if available
            final String privateKey = getPrivateKey();
            if (StringUtils.isNotBlank(privateKey)) {
                final SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
                    @Override
                    protected void configure(OpenSshConfig.Host host, Session session) {
                        // set password if available
                        String password = getPassword();
                        if (StringUtils.isNotBlank(password)) {
                            session.setPassword(password);
                        }
                    }

                    @Override
                    protected JSch createDefaultJSch(FS fs) throws JSchException {
                        JSch defaultJSch = super.createDefaultJSch(fs);
                        defaultJSch.addIdentity(privateKey);
                        return defaultJSch;
                    }
                };
                cloneCommand.setTransportConfigCallback(new TransportConfigCallback() {
                    @Override
                    public void configure(Transport transport) {
                        if( transport instanceof SshTransport ) {
                            SshTransport sshTransport = (SshTransport) transport;
                            sshTransport.setSshSessionFactory(sshSessionFactory);
                        } else {
                            logger.warn("you are not using ssh protocol while using scm.ppk");
                        }
                    }
                });
                cloneCommand.setCredentialsProvider(new passphraseCredentialsProvider(getPassword()));
            } else {
                if (getUrlName() != null && getPassword() != null) {
                    cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(getUsername(), getPassword()));
                }
            }

            // clone repository
            git = cloneCommand.setURI(getUrl())
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
