/**
 *
 */
package io.apiman.gateway.platforms.servlet.connectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

/**
 * @author Marc Savy <msavy@redhat.com>
 *
 */
public class SSLSessionStrategy {

    private SSLContext sslContext;
    private String[] allowedProtocols;
    private String[] allowedCiphers;
    private HostnameVerifier hostnameVerifier;

    public SSLSessionStrategy(SSLContext sslContext, String[] allowedProtocols, String[] allowedCiphers,
            HostnameVerifier hostnameVerifier) {
        this.sslContext = sslContext;
        this.allowedProtocols = allowedProtocols;
        this.allowedCiphers = allowedCiphers;
        this.hostnameVerifier = hostnameVerifier;
    }

    /**
     * @return the sslContext
     */
    public SSLContext getSslContext() {
        return sslContext;
    }

    /**
     * @return the allowedProtocols
     */
    public String[] getAllowedProtocols() {
        return allowedProtocols;
    }

    /**
     * @return the allowedCiphers
     */
    public String[] getAllowedCiphers() {
        return allowedCiphers;
    }

    /**
     * @return the hostnameVerifier
     */
    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }



}
