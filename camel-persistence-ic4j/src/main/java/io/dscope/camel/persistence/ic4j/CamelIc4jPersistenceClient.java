/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.dscope.camel.persistence.ic4j;

import io.dscope.camel.persistence.core.PersistenceConfiguration;
import io.dscope.camel.persistence.core.exception.BackendUnavailableException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;

final class CamelIc4jPersistenceClient implements Ic4jPersistenceClient {

    private final PersistenceConfiguration configuration;
    private final ProducerTemplate producerTemplate;

    CamelIc4jPersistenceClient(PersistenceConfiguration configuration) {
        this.configuration = configuration;
        try {
            DefaultCamelContext camelContext = new DefaultCamelContext();
            camelContext.start();
            this.producerTemplate = camelContext.createProducerTemplate();
            this.producerTemplate.start();
        } catch (Exception ex) {
            throw new BackendUnavailableException("Unable to start IC4J Camel client", ex);
        }
    }

    @Override
    public Object update(String method, Object request) {
        return send("update", method, request);
    }

    @Override
    public Object query(String method, Object request) {
        return send("query", method, request);
    }

    private Object send(String methodType, String method, Object request) {
        try {
            return producerTemplate.requestBody(uri(methodType, method), request);
        } catch (RuntimeException ex) {
            throw new BackendUnavailableException("IC4J call failed for method " + method, ex);
        }
    }

    private String uri(String methodType, String method) {
        StringBuilder uri = new StringBuilder("ic:")
            .append(methodType)
            .append("?url=")
            .append(parameter(configuration.icpReplicaUrl()))
            .append("&method=")
            .append(parameter(method))
            .append("&canisterId=")
            .append(parameter(configuration.icpCanisterId()))
            .append("&inType=jackson&outType=jackson")
            .append("&loadIDL=")
            .append(configuration.icpLoadIdl())
            .append("&fetchRootKey=")
            .append(configuration.icpFetchRootKey())
            .append("&waiterTimeout=")
            .append(configuration.icpWaiterTimeout())
            .append("&waiterSleep=")
            .append(configuration.icpWaiterSleep());
        if (!configuration.icpIdlFile().isBlank()) {
            uri.append("&idlFile=").append(parameter(configuration.icpIdlFile()));
        }
        return uri.toString();
    }

    private static String parameter(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}