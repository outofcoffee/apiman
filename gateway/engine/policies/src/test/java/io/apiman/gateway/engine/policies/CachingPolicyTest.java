/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apiman.gateway.engine.policies;

import io.apiman.test.common.mock.EchoResponse;
import io.apiman.test.policies.ApimanPolicyTest;
import io.apiman.test.policies.Configuration;
import io.apiman.test.policies.PolicyTestRequest;
import io.apiman.test.policies.PolicyTestRequestType;
import io.apiman.test.policies.PolicyTestResponse;
import io.apiman.test.policies.TestingPolicy;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit test.
 *
 * @author eric.wittmann@redhat.com
 */
@TestingPolicy(CachingPolicy.class)
@SuppressWarnings("nls")
public class CachingPolicyTest extends ApimanPolicyTest {

    @Test
    @Configuration("{" +
            "  \"ttl\" : 2" +
            "}")
    public void testCaching() throws Throwable {
        PolicyTestRequest request = PolicyTestRequest.build(PolicyTestRequestType.GET, "/some/cached-resource");

        PolicyTestResponse response = send(request);
        EchoResponse echo = response.entity(EchoResponse.class);
        assertNotNull(echo);
        Long counterValue = echo.getCounter();
        assertNotNull(counterValue);
        assertEquals("application/json", response.header("Content-Type"));

        // Now send the request again - we should get the *same* counter value!
        response = send(request);
        echo = response.entity(EchoResponse.class);
        assertNotNull(echo);
        Long counterValue2 = echo.getCounter();
        assertNotNull(counterValue2);
        assertEquals(counterValue, counterValue2);
        assertEquals("application/json", response.header("Content-Type"));

        // One more time, just to be sure
        response = send(request);
        echo = response.entity(EchoResponse.class);
        assertNotNull(echo);
        Long counterValue3 = echo.getCounter();
        assertNotNull(counterValue3);
        assertEquals(counterValue, counterValue3);
        assertEquals("application/json", response.header("Content-Type"));

        // Now wait for 3s and make sure the cache entry expired
        Thread.sleep(3000);
        response = send(request);
        echo = response.entity(EchoResponse.class);
        assertNotNull(echo);
        Long counterValue4 = echo.getCounter();
        assertNotNull(counterValue4);
        assertNotEquals(counterValue, counterValue4);
        assertEquals("application/json", response.header("Content-Type"));

        // And again - should be re-cached
        response = send(request);
        echo = response.entity(EchoResponse.class);
        assertNotNull(echo);
        Long counterValue5 = echo.getCounter();
        assertNotNull(counterValue5);
        assertEquals(counterValue4, counterValue5);
        assertEquals("application/json", response.header("Content-Type"));
    }

    /**
     * Verify that the query string is used as part of the cache key - expect
     * that requests with different query strings are treated as different, from
     * a caching perspective.
     */
    @Test
    @Configuration("{" +
            "  \"ttl\" : 2," +
            "  \"includeQueryInKey\" : true" +
            "}")
    public void testCachingUsingQueryString() throws Throwable {
        final String originalUri = "/some/cached-resource?foo=bar";

        PolicyTestResponse response = send(PolicyTestRequest.build(PolicyTestRequestType.GET, originalUri));
        EchoResponse echo = response.entity(EchoResponse.class);
        assertNotNull(echo);
        Long counterValue = echo.getCounter();
        assertNotNull(counterValue);
        assertEquals("application/json", response.header("Content-Type"));
        assertEquals(200, response.code());

        // Request with a different query string - expect an uncached response
        response = send(PolicyTestRequest.build(PolicyTestRequestType.GET, "/some/cached-resource?foo=different"));
        echo = response.entity(EchoResponse.class);
        assertNotNull(echo);
        Long counterValue2 = echo.getCounter();
        assertNotNull(counterValue2);
        assertNotEquals(counterValue, counterValue2);
        assertEquals("application/json", response.header("Content-Type"));
        assertEquals(200, response.code());

        // Request the original URI (including query string) - expect a cached response
        response = send(PolicyTestRequest.build(PolicyTestRequestType.GET, originalUri));
        echo = response.entity(EchoResponse.class);
        assertNotNull(echo);
        Long counterValue3 = echo.getCounter();
        assertNotNull(counterValue3);
        assertEquals(counterValue, counterValue3);
        assertEquals("application/json", response.header("Content-Type"));
        assertEquals(200, response.code());
    }
}
