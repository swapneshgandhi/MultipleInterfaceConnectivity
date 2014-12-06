/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


// todo: make object of a delegate class (that extends URLConnection class) 
// and use that object to access the functions that are presently accessed from HttpURLConnection class.
// For functions used here (from HTTPURLConnection) that are not present in URLConnection, replicate from HttpURLConnection.

package libcore.net.http;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.CacheResponse;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.SocketPermission;
import java.net.URLConnection;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.SecureCacheResponse;
import java.net.URL;
import java.security.Permission;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import libcore.io.Base64;
import libcore.io.IoUtils;
import java.nio.charset.Charsets;


final class HttpsURLConnectionImpl extends HttpsURLConnection {

    /** HttpUrlConnectionDelegate allows reuse of HttpURLConnectionImpl */
    private final HttpUrlConnectionDelegate delegate;

    protected HttpsURLConnectionImpl(URL url, int port) {
        super(url);
        System.out.println("MIC : HttpsURLConnectionImpl(url  = "+url.toString()+", port = "+Integer.toString(port)+")");
        delegate = new HttpUrlConnectionDelegate(url, port);
        
    }

    protected HttpsURLConnectionImpl(URL url, int port, Proxy proxy) {
        super(url);
        System.out.println("MIC : HttpsURLConnectionImpl(URL = "+url.toString()+", port = "+Integer.toString(port)+", proxy = "+proxy.toString()+")");
        delegate = new HttpUrlConnectionDelegate(url, port, proxy);
    }

    private void checkConnected() {
    	
    	System.out.println("MIC : HttpsURLConnectionImpl: checkConnected()");
        if (delegate.getSSLSocket() == null) {
            throw new IllegalStateException("Connection has not yet been established");
        }
    }

    HttpEngine getHttpEngine() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getHttpEngine()");
        return delegate.getHttpEngine();
    }

    @Override
    public String getCipherSuite() {
    	
    	System.out.println("MIC : HttpsURLConnectionImpl: getCipherSuite()");
        SecureCacheResponse cacheResponse = delegate.getCacheResponse();
        if (cacheResponse != null) {
            return cacheResponse.getCipherSuite();
        }
        checkConnected();
        return delegate.getSSLSocket().getSession().getCipherSuite();
    }

    @Override
    public Certificate[] getLocalCertificates() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getLocalCertificates()");
        SecureCacheResponse cacheResponse = delegate.getCacheResponse();
        if (cacheResponse != null) {
            List<Certificate> result = cacheResponse.getLocalCertificateChain();
            return result != null ? result.toArray(new Certificate[result.size()]) : null;
        }
        checkConnected();
        System.out.println("MIC : HttpsURLConnectionImpl: getLocalCertificates() -> Calling delegate.getSSLSocket().getSession.getPeerCertificates()");
        return delegate.getSSLSocket().getSession().getLocalCertificates();
    }

    @Override
    public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException {
    	System.out.println("MIC : HttpsURLConnectionImpl: getServerCertificates()");
        SecureCacheResponse cacheResponse = delegate.getCacheResponse();
        if (cacheResponse != null) {
            List<Certificate> result = cacheResponse.getServerCertificateChain();
            return result != null ? result.toArray(new Certificate[result.size()]) : null;
        }
        System.out.println("MIC : HttpsURLConnectionImpl: getServerCertificates() -> Calling checkConnected()");
        checkConnected();
        System.out.println("MIC : HttpsURLConnectionImpl: getServerCertificates() -> Calling delegate.getSSLSocket().getSession.getPeerCertificates()");
        return delegate.getSSLSocket().getSession().getPeerCertificates();
    }

    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
    	System.out.println("MIC : HttpsURLConnectionImpl: getPeerPrincipal()");
        SecureCacheResponse cacheResponse = delegate.getCacheResponse();
        if (cacheResponse != null) {
            return cacheResponse.getPeerPrincipal();
        }
        
        checkConnected();
        System.out.println("MIC : HttpsURLConnectionImpl: getPeerPrincipal() -> Calling delegate.getSSLSocket().getSession.getPeerCertificates()");
        return delegate.getSSLSocket().getSession().getPeerPrincipal();
    }

    @Override
    public Principal getLocalPrincipal() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getLocalPrincipal()");
        SecureCacheResponse cacheResponse = delegate.getCacheResponse();
        if (cacheResponse != null) {
            return cacheResponse.getLocalPrincipal();
        }
        checkConnected();
        System.out.println("MIC : HttpsURLConnectionImpl: getLocalPrincipal() -> Calling delegate.getSSLSocket().getSession.getPeerCertificates()");
        return delegate.getSSLSocket().getSession().getLocalPrincipal();
    }

    @Override
    public void disconnect() {
    	System.out.println("MIC : HttpsURLConnectionImpl: disconnect()");
    	System.out.println("MIC : HttpsURLConnectionImpl: disconnect() -> calling delegate.disconnect()");
        delegate.disconnect();
    }

    @Override
    public InputStream getErrorStream() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getErrorStream()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getErrorStream() -> calling delegate.getErrorStream()");
        return delegate.getErrorStream();
    }

    @Override
    public String getRequestMethod() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getRequestMethod()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getRequestMethod() -> calling delegate.getRequestMethod()");
        return delegate.getRequestMethod();
    }

    @Override
    public int getResponseCode() throws IOException {
    	System.out.println("MIC : HttpsURLConnectionImpl: Header: getResponseCode()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getResponseCode() -> calling delegate.getResponseCode()");
    	int resCode = delegate.getResponseCode();
		System.out.println("MIC : HttpsURLConnectionImpl: Header: getResponseCode() = "+Integer.toString(resCode));
        return resCode;
    }

    @Override
    public String getResponseMessage() throws IOException {
    	System.out.println("MIC : HttpsURLConnectionImpl: getResponseMessage()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getResponseMessage() -> calling delegate.getResponseMessage()");
        return delegate.getResponseMessage();
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
    	System.out.println("MIC : HttpsURLConnectionImpl: setRequestMethod("+method+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: setRequestMethod() -> calling delegate.setRequestMethod()");
        delegate.setRequestMethod(method);
        /*
        if(method == HttpEngine.POST || method == HttpEngine.PUT)
        {
        	delegate.isVanilla = true;
        }
        else
        {
        	delegate.isVanilla = false;
        }
        */
    }

    @Override
    public boolean usingProxy() {
    	System.out.println("MIC : HttpsURLConnectionImpl: usingProxy()");
    	System.out.println("MIC : HttpsURLConnectionImpl: usingProxy() -> calling delegate.usingProxy()");
        return delegate.usingProxy();
    }

    @Override
    public boolean getInstanceFollowRedirects() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getInstanceFollowRedirects()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getInstanceFollowRedirects() -> calling delegate.getInstanceFollowRedirects()");
        return delegate.getInstanceFollowRedirects();
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
    	System.out.println("MIC : HttpsURLConnectionImpl: setInstanceFollowRedirects()");
    	System.out.println("MIC : HttpsURLConnectionImpl: setInstanceFollowRedirects() -> calling delegate.setInstanceFollowRedirects()");
    	delegate.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public void connect() throws IOException {
        connected = true;
        System.out.println("MIC : HttpsURLConnectionImpl: connect()");
    	System.out.println("MIC : HttpsURLConnectionImpl: connect() -> calling delegate.connect()");
        delegate.connect();
    }

    @Override
    public boolean getAllowUserInteraction() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getAllowUserInteraction()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getAllowUserInteraction() -> calling delegate.getAllowUserInteraction()");
        return delegate.getAllowUserInteraction();
    }

    @Override
    public Object getContent() throws IOException {
    	System.out.println("MIC : HttpsURLConnectionImpl: getContent()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getContent() -> calling delegate.getContent()");
        return delegate.getContent();
    }

    @SuppressWarnings("unchecked") // Spec does not generify
    @Override
    public Object getContent(Class[] types) throws IOException {
    	System.out.println("MIC : HttpsURLConnectionImpl: getContent(types)");
    	System.out.println("MIC : HttpsURLConnectionImpl: getContent(types) -> calling delegate.getContent(types)");
        return delegate.getContent(types);
    }

    @Override
    public String getContentEncoding() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getContentEncoding()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getContentEncoding() -> calling delegate.getContentEncoding()");
        return delegate.getContentEncoding();
    }

    @Override
    public int getContentLength() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getContentLength()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getContentLength() -> calling delegate.getContentLength()");
        return delegate.getContentLength();
    }

    @Override
    public String getContentType() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getContentType()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getContentType() -> calling delegate.getContentType()");
    	return delegate.getContentType();
    }

    @Override
    public long getDate() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getDate()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getDate() -> calling delegate.getDate()");
        return delegate.getDate();
    }

    @Override
    public boolean getDefaultUseCaches() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getDefaultUseCaches()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getDefaultUseCaches() -> calling delegate.getDefaultUseCaches()");
        return delegate.getDefaultUseCaches();
    }

    @Override
    public boolean getDoInput() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getDoInput()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getDoInput() -> calling delegate.getDoInput()");
        return delegate.getDoInput();
    }

    @Override
    public boolean getDoOutput() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getDoOutput()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getDoOutput() -> calling delegate.getDoOutput()");
        return delegate.getDoOutput();
    }

    @Override
    public long getExpiration() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getExpiration()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getExpiration() -> calling delegate.getExpiration()");
        return delegate.getExpiration();
    }

    @Override
    public String getHeaderField(int pos) {
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderField("+Integer.toString(pos)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderField("+Integer.toString(pos)+") -> calling delegate.getHeaderField("+Integer.toString(pos)+")");
        return delegate.getHeaderField(pos);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderFields()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderFields() -> calling delegate.getHeaderFields()");
        return delegate.getHeaderFields();
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getRequestProperties()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getRequestProperties() -> calling delegate.getRequestProperties()");
        return delegate.getRequestProperties();
    }

    @Override
    public void addRequestProperty(String field, String newValue) {
    	System.out.println("MIC : HttpsURLConnectionImpl: addRequestProperty(field = "+field+", newValue = "+newValue+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: addRequestProperty(field = "+field+", newValue = "+newValue+") -> calling delegate.addRequestProperty(field = "+field+", newValue = "+newValue+")");
        delegate.addRequestProperty(field, newValue);
    }

    @Override
    public String getHeaderField(String key) {
    	System.out.println("MIC : HttpsURLConnectionImpl: Header: getHeaderField("+key+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderField("+key+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderField("+key+") -> calling delegate.getHeaderField("+key+")");
    	String headerVal = delegate.getHeaderField(key);
    	if(headerVal != null)
    		System.out.println("MIC : HttpsURLConnectionImpl: Header: getHeaderField("+key+") = "+headerVal);
    	else
    		System.out.println("MIC : HttpsURLConnectionImpl: Header: getHeaderField("+key+") = null");
        return headerVal;
    }

    @Override
    public long getHeaderFieldDate(String field, long defaultValue) {
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderFieldDate(field = "+field+", defaultValue = "+Long.toString(defaultValue)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderFieldDate(field = "+field+", defaultValue = "+Long.toString(defaultValue)+") -> calling delegate.getHeaderFieldDate(field = "+field+", defaultValue = "+Long.toString(defaultValue)+")");
        return delegate.getHeaderFieldDate(field, defaultValue);
    }

    @Override
    public int getHeaderFieldInt(String field, int defaultValue) {
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderFieldInt(field = "+field+", defaultValue = "+Integer.toString(defaultValue)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderFieldInt(field = "+field+", defaultValue = "+Integer.toString(defaultValue)+") -> calling delegate.getHeaderFieldInt(field = "+field+", defaultValue = "+Integer.toString(defaultValue)+")");
        return delegate.getHeaderFieldInt(field, defaultValue);
    }

    @Override
    public String getHeaderFieldKey(int posn) {
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderFieldKey("+Integer.toString(posn)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: getHeaderFieldKey("+Integer.toString(posn)+") -> calling delegate.getHeaderFieldKey("+Integer.toString(posn)+")");
        return delegate.getHeaderFieldKey(posn);
    }

    @Override
    public long getIfModifiedSince() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getIfModifiedSince()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getIfModifiedSince() -> calling delegate.getIfModifiedSince()");
        return delegate.getIfModifiedSince();
    }

    @Override
    public InputStream getInputStream() throws IOException {
    	System.out.println("MIC : HttpsURLConnectionImpl: getInputStream()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getInputStream() -> calling delegate.getInputStream()");
        return delegate.getInputStream();
    }

    @Override
    public long getLastModified() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getLastModified()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getLastModified() -> calling delegate.getLastModified()");
        return delegate.getLastModified();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
    	System.out.println("MIC : HttpsURLConnectionImpl: getOutputStream()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getOutputStream() -> calling delegate.getOutputStream()");
        return delegate.getOutputStream();
    }

    @Override
    public Permission getPermission() throws IOException {
    	System.out.println("MIC : HttpsURLConnectionImpl: getPermission()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getPermission() -> calling delegate.getPermission()");
        return delegate.getPermission();
    }

    @Override
    public String getRequestProperty(String field) {
    	System.out.println("MIC : HttpsURLConnectionImpl: getRequestProperty("+field+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: getRequestProperty("+field+") -> calling delegate.getRequestProperty("+field+")");
        return delegate.getRequestProperty(field);
    }

    @Override
    public URL getURL() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getURL()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getURL() -> calling delegate.getURL()");
        return delegate.getURL();
    }

    @Override
    public boolean getUseCaches() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getUseCaches()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getUseCaches() -> calling delegate.getUseCaches()");
        return delegate.getUseCaches();
    }

    @Override
    public void setAllowUserInteraction(boolean newValue) {
    	System.out.println("MIC : HttpsURLConnectionImpl: setAllowUserInteraction("+Boolean.toString(newValue)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: setAllowUserInteraction("+Boolean.toString(newValue)+") -> calling delegate.setAllowUserInteraction("+Boolean.toString(newValue)+")");
        delegate.setAllowUserInteraction(newValue);
    }

    @Override
    public void setDefaultUseCaches(boolean newValue) {
    	System.out.println("MIC : HttpsURLConnectionImpl: setDefaultUseCaches("+Boolean.toString(newValue)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: setDefaultUseCaches("+Boolean.toString(newValue)+") -> calling delegate.setDefaultUseCaches("+Boolean.toString(newValue)+")");
        delegate.setDefaultUseCaches(newValue);
    }

    @Override
    public void setDoInput(boolean newValue) {
    	System.out.println("MIC : HttpsURLConnectionImpl: setDoInput("+Boolean.toString(newValue)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: setDoInput("+Boolean.toString(newValue)+") -> calling delegate.setDoInput("+Boolean.toString(newValue)+")");
        delegate.setDoInput(newValue);
    }

    @Override
    public void setDoOutput(boolean newValue) {
    	System.out.println("MIC : HttpsURLConnectionImpl: setDoOutput("+Boolean.toString(newValue)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: setDoOutput("+Boolean.toString(newValue)+") -> calling delegate.setDoOutput("+Boolean.toString(newValue)+")");
        delegate.setDoOutput(newValue);
        //delegate.isVanilla = true;
    }

    @Override
    public void setIfModifiedSince(long newValue) {
    	System.out.println("MIC : HttpsURLConnectionImpl: getContentLength()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getContentLength() -> calling delegate.getContentLength()");
        delegate.setIfModifiedSince(newValue);
    }

    @Override
    public void setRequestProperty(String field, String newValue) {
    	System.out.println("MIC : HttpsURLConnectionImpl: setRequestProperty(field = "+field+", newValue = "+newValue+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: setRequestProperty(field = "+field+", newValue = "+newValue+") -> calling delegate.setRequestProperty(field = "+field+", newValue = "+newValue+")");
        delegate.setRequestProperty(field, newValue);
    }

    @Override
    public void setUseCaches(boolean newValue) {
    	System.out.println("MIC : HttpsURLConnectionImpl: setUseCaches("+Boolean.toString(newValue)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: setUseCaches("+Boolean.toString(newValue)+") -> calling delegate.setUseCaches("+Boolean.toString(newValue)+")");
        delegate.setUseCaches(newValue);
    }

    @Override
    public void setConnectTimeout(int timeoutMillis) {
    	System.out.println("MIC : HttpsURLConnectionImpl: setConnectTimeout("+Integer.toString(timeoutMillis)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: setConnectTimeout("+Integer.toString(timeoutMillis)+") -> calling delegate.setConnectTimeout("+Integer.toString(timeoutMillis)+")");
        delegate.setConnectTimeout(timeoutMillis);
    }

    @Override
    public int getConnectTimeout() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getConnectTimeout()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getConnectTimeout() -> calling delegate.getConnectTimeout()");
        return delegate.getConnectTimeout();
    }

    @Override
    public void setReadTimeout(int timeoutMillis) {
    	System.out.println("MIC : HttpsURLConnectionImpl: setReadTimeout("+Integer.toString(timeoutMillis)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: setReadTimeout("+Integer.toString(timeoutMillis)+") -> calling delegate.setReadTimeout("+Integer.toString(timeoutMillis)+")");
        delegate.setReadTimeout(timeoutMillis);
    }

    @Override
    public int getReadTimeout() {
    	System.out.println("MIC : HttpsURLConnectionImpl: getReadTimeout()");
    	System.out.println("MIC : HttpsURLConnectionImpl: getReadTimeout() -> calling delegate.getReadTimeout()");
        return delegate.getReadTimeout();
    }

    @Override
    public String toString() {
    	System.out.println("MIC : HttpsURLConnectionImpl: toString()");
    	System.out.println("MIC : HttpsURLConnectionImpl: toString() -> calling delegate.toString()");
        return delegate.toString();
    }

    @Override
    public void setFixedLengthStreamingMode(int contentLength) {
    	System.out.println("MIC : HttpsURLConnectionImpl: getContentLength(contentLength = "+Integer.toString(contentLength)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: getContentLength(contentLength = "+Integer.toString(contentLength)+") -> calling delegate.getContentLength(contentLength = "+Integer.toString(contentLength)+")");
        delegate.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setChunkedStreamingMode(int chunkLength) {
    	System.out.println("MIC : HttpsURLConnectionImpl: setChunkedStreamingMode(chunklength = "+Integer.toString(chunkLength)+")");
    	System.out.println("MIC : HttpsURLConnectionImpl: setChunkedStreamingMode(chunklength = "+Integer.toString(chunkLength)+") -> calling delegate.setChunkedStreamingMode(chunklength = "+Integer.toString(chunkLength)+")");
        delegate.setChunkedStreamingMode(chunkLength);
    }

    
    public final class HttpUrlConnectionDelegate extends HttpURLConnectionImpl {
        public HttpUrlConnectionDelegate(URL url, int port) {
            super(url, port);
            this.setHttps(true);
            System.out.println("MIC : HttpsURLConnectionImpl: HttpUrlConnectionDelegate: HttpUrlConnectionDelegate(url = "+url.toString()+", port = "+Integer.toString(port)+")");
        }

        public HttpUrlConnectionDelegate(URL url, int port, Proxy proxy) {
            super(url, port, proxy);
            this.setHttps(true);
            System.out.println("MIC : HttpsURLConnectionImpl: HttpUrlConnectionDelegate: HttpUrlConnectionDelegate(url = "+url.toString()+", port = "+Integer.toString(port)+", proxy = "+proxy.toString()+")");
        }

        @Override protected HttpEngine newHttpEngine(String method, RawHeaders requestHeaders,
                HttpConnection connection, RetryableOutputStream requestBody) throws IOException {
        	System.out.println("MIC : HttpsURLConnectionImpl: HttpUrlConnectionDelegate: newHttpEngine( method = "+method+")");
            return new HttpsEngine(this, method, requestHeaders, connection, requestBody,
                    HttpsURLConnectionImpl.this);
        }
        
        @Override protected HttpEngine newHttpEngine(int type, String method, RawHeaders requestHeaders,
                HttpConnection connection, RetryableOutputStream requestBody) throws IOException {
        	System.out.println("MIC : HttpsURLConnectionImpl: HttpUrlConnectionDelegate: newHttpEngine( method = "+method+")");
            return new HttpsEngine(type, this, method, requestHeaders, connection, requestBody,
                    HttpsURLConnectionImpl.this);
        }

        public SecureCacheResponse getCacheResponse() {
        	System.out.println("MIC : HttpsURLConnectionImpl: HttpUrlConnectionDelegate: getCacheResponse()");
            HttpsEngine engine = (HttpsEngine) httpEngine;
            return engine != null ? (SecureCacheResponse) engine.getCacheResponse() : null;
        }

        public SSLSocket getSSLSocket() {
        	System.out.println("MIC : HttpsURLConnectionImpl: HttpUrlConnectionDelegate: getSSLSocket()");
            HttpsEngine engine = (HttpsEngine) httpEngine;
            return engine != null ? engine.sslSocket : null;
        }
    }
    

    private static class HttpsEngine extends HttpEngine {

        /**
         * Local stash of HttpsEngine.connection.sslSocket for answering
         * queries such as getCipherSuite even after
         * httpsEngine.Connection has been recycled. It's presence is also
         * used to tell if the HttpsURLConnection is considered connected,
         * as opposed to the connected field of URLConnection or the a
         * non-null connect in HttpURLConnectionImpl
         */
        private SSLSocket sslSocket;

        private final HttpsURLConnectionImpl enclosing;

        /**
         * @param policy the HttpURLConnectionImpl with connection configuration
         * @param enclosing the HttpsURLConnection with HTTPS features
         */
        private HttpsEngine(HttpURLConnectionImpl policy, String method, RawHeaders requestHeaders,
                HttpConnection connection, RetryableOutputStream requestBody,
                HttpsURLConnectionImpl enclosing) throws IOException {
            super(-1, policy, method, requestHeaders, connection, requestBody);
            this.sslSocket = connection != null ? connection.getSecureSocketIfConnected() : null;
            this.enclosing = enclosing;
            System.out.println("MIC : HttpsURLConnectionImpl: HttpsEngine: HttpsEngine()");
        }
        
        private HttpsEngine(int type, HttpURLConnectionImpl policy, String method, RawHeaders requestHeaders,
                HttpConnection connection, RetryableOutputStream requestBody,
                HttpsURLConnectionImpl enclosing) throws IOException {
            super(type, policy, method, requestHeaders, connection, requestBody);
            this.sslSocket = connection != null ? connection.getSecureSocketIfConnected() : null;
            this.enclosing = enclosing;
            System.out.println("MIC : HttpsURLConnectionImpl: HttpsEngine: HttpsEngine()");
        }

        @Override protected void connect() throws IOException {
            // first try an SSL connection with compression and
            // various TLS extensions enabled, if it fails (and its
            // not unheard of that it will) fallback to a more
            // barebones connections
        	System.out.println("MIC : HttpsURLConnectionImpl: HttpsEngine: connect()");
            boolean connectionReused;
            try {
                connectionReused = makeSslConnection(true); /////////////////
            } catch (IOException e) {
                // If the problem was a CertificateException from the X509TrustManager,
                // do not retry, we didn't have an abrupt server initiated exception.
                if (e instanceof SSLHandshakeException
                        && e.getCause() instanceof CertificateException) {
                    throw e;
                }
                release(false);
                connectionReused = makeSslConnection(false);
            }

            if (!connectionReused) {
                sslSocket = connection.verifySecureSocketHostname(enclosing.getHostnameVerifier());
            }
        }

        /**
         * Attempt to make an https connection. Returns true if a
         * connection was reused, false otherwise.
         *
         * @param tlsTolerant If true, assume server can handle common
         * TLS extensions and SSL deflate compression. If false, use
         * an SSL3 only fallback mode without compression.
         */
        private boolean makeSslConnection(boolean tlsTolerant) throws IOException {
            // make an SSL Tunnel on the first message pair of each SSL + proxy connection
        	System.out.println("MIC : HttpsURLConnectionImpl: HttpsEngine: makeSslConnection(tlsTolerant = "+Boolean.toString(tlsTolerant)+")");
            if (connection == null) {
                connection = openSocketConnection();
                if (connection.getAddress().getProxy() != null) {
                    makeTunnel(policy, connection, getRequestHeaders());
                }
            }

            // if super.makeConnection returned a connection from the
            // pool, sslSocket needs to be initialized here. If it is
            // a new connection, it will be initialized by
            // getSecureSocket below.
            sslSocket = connection.getSecureSocketIfConnected();

            // we already have an SSL connection,
            if (sslSocket != null) {
                return true;
            }

            connection.setupSecureSocket(enclosing.getSSLSocketFactory(), tlsTolerant);        ///////////////////////
            return false;
        }

        /**
         * To make an HTTPS connection over an HTTP proxy, send an unencrypted
         * CONNECT request to create the proxy connection. This may need to be
         * retried if the proxy requires authorization.
         */
        private void makeTunnel(HttpURLConnectionImpl policy, HttpConnection connection,
                RequestHeaders requestHeaders) throws IOException {
        	System.out.println("MIC : HttpsURLConnectionImpl: HttpsEngine: makeTunnel()");
            RawHeaders rawRequestHeaders = requestHeaders.getHeaders();
            while (true) {
                HttpEngine connect = new ProxyConnectEngine(policy, rawRequestHeaders, connection);
                connect.sendRequest();
                connect.readResponse();

                int responseCode = connect.getResponseCode();
                switch (connect.getResponseCode()) {
                case HTTP_OK:
                    return;
                case HTTP_PROXY_AUTH:
                    rawRequestHeaders = new RawHeaders(rawRequestHeaders);
                    boolean credentialsFound = policy.processAuthHeader(HTTP_PROXY_AUTH,
                            connect.getResponseHeaders(), rawRequestHeaders);
                    if (credentialsFound) {
                        continue;
                    } else {
                        throw new IOException("Failed to authenticate with proxy");
                    }
                default:
                    throw new IOException("Unexpected response code for CONNECT: " + responseCode);
                }
            }
        }

        @Override protected boolean acceptCacheResponseType(CacheResponse cacheResponse) {
        	System.out.println("MIC : HttpsURLConnectionImpl: HttpsEngine: acceptCacheResponseType(CacheResponse)");
            return cacheResponse instanceof SecureCacheResponse;
        }

        @Override protected boolean includeAuthorityInRequestLine() {
            // Even if there is a proxy, it isn't involved. Always request just the file.
        	System.out.println("MIC : HttpsURLConnectionImpl: HttpsEngine: includeAuthorityInRequestLine()");
            return false;
        }

        @Override protected SSLSocketFactory getSslSocketFactory() {
        	System.out.println("MIC : HttpsURLConnectionImpl: HttpsEngine: getSslSocketFactory()");
            return enclosing.getSSLSocketFactory();
        }

        @Override protected HttpURLConnection getHttpConnectionToCache() {
        	System.out.println("MIC : HttpsURLConnectionImpl: HttpsEngine: getHttpConnectionToCache()");
            return enclosing;
        }
    }

    private static class ProxyConnectEngine extends HttpEngine {
        public ProxyConnectEngine(HttpURLConnectionImpl policy, RawHeaders requestHeaders,
                HttpConnection connection) throws IOException {
            super(-1, policy, HttpEngine.CONNECT, requestHeaders, connection, null);
            System.out.println("MIC : HttpsURLConnectionImpl: ProxyConnectEngine: ProxyConnectEngine(HttpURLConnectionImpl policy, requestHeaders, HttpConnection connection)");
        }

        /**
         * If we're establishing an HTTPS tunnel with CONNECT (RFC 2817 5.2), send
         * only the minimum set of headers. This avoids sending potentially
         * sensitive data like HTTP cookies to the proxy unencrypted.
         */
        @Override protected RawHeaders getNetworkRequestHeaders() throws IOException {
        	System.out.println("MIC : HttpsURLConnectionImpl: ProxyConnectEngine: getNetworkRequestHeaders()");
            RequestHeaders privateHeaders = getRequestHeaders();
            URL url = policy.getURL();

            RawHeaders result = new RawHeaders();
            result.setStatusLine("CONNECT " + url.getHost() + ":" + url.getEffectivePort()
                    + " HTTP/1.1");

            // Always set Host and User-Agent.
            String host = privateHeaders.getHost();
            if (host == null) {
                host = getOriginAddress(url);
            }
            result.set("Host", host);

            String userAgent = privateHeaders.getUserAgent();
            if (userAgent == null) {
                userAgent = getDefaultUserAgent();
            }
            result.set("User-Agent", userAgent);

            // Copy over the Proxy-Authorization header if it exists.
            String proxyAuthorization = privateHeaders.getProxyAuthorization();
            if (proxyAuthorization != null) {
                result.set("Proxy-Authorization", proxyAuthorization);
            }

            // Always set the Proxy-Connection to Keep-Alive for the benefit of
            // HTTP/1.0 proxies like Squid.
            result.set("Proxy-Connection", "Keep-Alive");
            return result;
        }

        @Override protected boolean requiresTunnel() {
        	System.out.println("MIC : HttpsURLConnectionImpl: ProxyConnectEngine: requiresTunnel()");
            return true;
        }
    }
}

