/*
 * Copyright 2026 - 2026 Dmitri Snytkine. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.snytkine.apitester.api_tester_cli.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProxyTunnelingSupport}.
 *
 * <p>The property under test is global JVM state, so each test saves and restores it. Note that
 * these tests verify the <em>setting</em> logic only — that the cleared property actually makes the
 * JDK answer a tunneled {@code 407} is proven end-to-end by {@code PureJavaTestEngineProxyTest}.
 */
class ProxyTunnelingSupportTest {

    private String original;

    /** Remembers the ambient property value so it can be restored. */
    @BeforeEach
    void rememberProperty() {
        original = System.getProperty(ProxyTunnelingSupport.TUNNELING_DISABLED_SCHEMES);
    }

    /** Restores the ambient property value. */
    @AfterEach
    void restoreProperty() {
        if (original == null) {
            System.clearProperty(ProxyTunnelingSupport.TUNNELING_DISABLED_SCHEMES);
        } else {
            System.setProperty(ProxyTunnelingSupport.TUNNELING_DISABLED_SCHEMES, original);
        }
    }

    @Test
    void clearsTheDisabledSchemesWhenUnset() {
        System.clearProperty(ProxyTunnelingSupport.TUNNELING_DISABLED_SCHEMES);

        ProxyTunnelingSupport.enableBasicAuthenticationOverConnect();

        assertThat(System.getProperty(ProxyTunnelingSupport.TUNNELING_DISABLED_SCHEMES))
                .isEmpty();
    }

    /**
     * An operator who sets the property on the command line has deliberately chosen a policy; the
     * application must not quietly overwrite it.
     */
    @Test
    void doesNotOverrideAnExplicitlyConfiguredValue() {
        System.setProperty(ProxyTunnelingSupport.TUNNELING_DISABLED_SCHEMES, "Basic");

        ProxyTunnelingSupport.enableBasicAuthenticationOverConnect();

        assertThat(System.getProperty(ProxyTunnelingSupport.TUNNELING_DISABLED_SCHEMES))
                .isEqualTo("Basic");
    }

    @Test
    void isIdempotent() {
        System.clearProperty(ProxyTunnelingSupport.TUNNELING_DISABLED_SCHEMES);

        ProxyTunnelingSupport.enableBasicAuthenticationOverConnect();
        ProxyTunnelingSupport.enableBasicAuthenticationOverConnect();

        assertThat(System.getProperty(ProxyTunnelingSupport.TUNNELING_DISABLED_SCHEMES))
                .isEmpty();
    }
}
