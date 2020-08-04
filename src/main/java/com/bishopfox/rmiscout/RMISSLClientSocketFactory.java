package com.bishopfox.rmiscout;

import java.io.*;
import java.net.*;
import java.rmi.server.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

public class RMISSLClientSocketFactory implements RMIClientSocketFactory, Serializable {

    private SSLContext allTrustingSSLContext;

    public RMISSLClientSocketFactory() {
        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs,
                                               String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs,
                                               String authType) {
                }
            } };

            allTrustingSSLContext = SSLContext.getInstance("SSL");
            allTrustingSSLContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    public Socket createSocket(String host, int port) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) allTrustingSSLContext.getSocketFactory();
        SSLSocket socket = (SSLSocket)factory.createSocket(host, port);
        return socket;
    }
}