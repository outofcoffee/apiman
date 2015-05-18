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
package io.apiman.gateway.platforms.servlet.connectors;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.Args;
/**
 * Factory to produce {@link SSLConnectionSocketFactory}.
 *
 * @author Marc Savy
 */
public class SSLSessionStrategyFactory {
    private SSLSessionStrategyFactory() {}
    private static final HostnameVerifier ALLOW_ANY = new AllowAnyVerifier();
    private static final TrustStrategy SELF_SIGNED = new TrustSelfSignedStrategy();

    /**
     * @param clientKeystore the client keystore (trust store)
     * @param keystorePassword password the keystore password
     * @param allowedProtocols the allowed transport protocols. <strong><em>Avoid insecure protocols</em></strong>
     * @param trustSelfSigned true if self signed certificates can be trusted. <strong><em>Use with caution.</em></strong>
     * @param allowedCiphers allowed crypto ciphersuites, <tt>null</tt> to use system defaults
     * @param allowAnyHostname true if any hostname can be connected to (i.e. does not need to match
     *            certificate hostname). <strong><em>Do not use in production</em></strong>.
     * @return the connection socket factory
     * @throws NoSuchAlgorithmException if the selected algorithm is not available on the system
     * @throws KeyStoreException if there was a problem with the keystore
     * @throws CertificateException if there was a problem with the certificate
     * @throws IOException if the truststore could not be found or was invalid
     * @throws KeyManagementException if there is a problem with keys
     */
    public static SSLSessionStrategy build(File clientKeystore,
            String keystorePassword,
            String[] allowedProtocols,
            String[] allowedCiphers,
            boolean trustSelfSigned,
            final boolean allowAnyHostname)

            throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, KeyManagementException {

        Args.notNull(clientKeystore, "Client keystore"); //$NON-NLS-1$
        Args.notNull(allowedProtocols, "Allowed protocols"); //$NON-NLS-1$
        Args.notNull(allowedCiphers, "Allowed ciphers"); //$NON-NLS-1$

        TrustStrategy trustStrategy = trustSelfSigned ?  SELF_SIGNED : null;
        HostnameVerifier hostnameVerifier = allowAnyHostname ? ALLOW_ANY : SSLConnectionSocketFactory.getDefaultHostnameVerifier();

        SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(clientKeystore,
                keystorePassword.toCharArray(),
                trustStrategy)
                .build();

        return new SSLSessionStrategy(sslContext,
                allowedProtocols,
                allowedCiphers,
                hostnameVerifier);
    }

    /**
     * Allows any hostname.
     *
     * @author Marc Savy <msavy@redhat.com>
     */
    private static final class AllowAnyVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}
