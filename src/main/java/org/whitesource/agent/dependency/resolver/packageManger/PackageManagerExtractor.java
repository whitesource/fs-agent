package org.whitesource.agent.dependency.resolver.packageManger;

import com.aragost.javahg.log.Logger;
import com.aragost.javahg.log.LoggerFactory;
import org.whitesource.agent.api.model.AgentProjectInfo;

import java.util.Collection;
import java.util.List;

/**
 * Created by anna.rozin
 */
public class PackageManagerExtractor {

    /* --- Statics Members --- */

    private static final Logger logger = LoggerFactory.getLogger(PackageManagerExtractor.class);

    private static final String WHITE_SPACE = " ";
    private static final String COLON = ":";
    private static final String NON_ASCII_CHARS = "[^\\x20-\\x7e]";
    private static final String EMPTY_STRING = "";

    private static final String[] DEBIAN_PACKAGES_LIST_COMMAND = new String[] { "dpkg", "-l" };
    private static final String DEBIAN_INSTALLED_PACKAGE_PREFIX = "ii";
    private static final int DEBIAN_PACKAGE_NAME_INDEX = 0;
    private static final int DEBIAN_PACKAGE_VERSION_INDEX = 1;
    private static final int DEBIAN_PACKAGE_ARCH_INDEX = 2;
    private static final String DEBIAN_PACKAGE_PATTERN = "{0}_{1}_{2}.deb";

    /* --- Members --- */


    /* --- Constructors --- */

    public PackageManagerExtractor() {
        //todo
    }

    /* --- Public methods --- */

    public Collection<AgentProjectInfo> createProjects() {

        // 1. create cmdObject for linux (look in npm/bower)
        // 2. run the comman
        // 3. parse debian/rpm/... packages
        // 4. create dependecy info for each package
        // 5. return

        //todo return Collection<AgentProjectInfo>
        return null;
    }





}
