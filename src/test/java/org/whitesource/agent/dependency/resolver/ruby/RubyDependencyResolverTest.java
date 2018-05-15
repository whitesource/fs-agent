package org.whitesource.agent.dependency.resolver.ruby;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class RubyDependencyResolverTest {

    RubyDependencyResolver rubyDependencyResolver;

    @Before
    public void setUp() throws Exception {
        rubyDependencyResolver = new RubyDependencyResolver();
    }

    @Test
    public void resolveDependencies() {
        rubyDependencyResolver.resolveDependencies("","C:\\Users\\ErezHuberman\\Documents\\ruby\\awesome-react-native-master",null);
    }
}