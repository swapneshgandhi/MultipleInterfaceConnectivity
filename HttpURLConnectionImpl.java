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

package libcore.net.http;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.SocketPermission;
import java.net.URL;
import java.nio.charset.Charsets;
import java.security.Permission;
import java.util.List;
import java.util.Map;
import libcore.io.Base64;
import libcore.io.IoUtils;

import java.io.FileReader;
import java.io.BufferedReader;

/**
 * This implementation uses HttpEngine to send requests and receive responses.
 * This class may use multiple HttpEngines to follow redirects, authentication
 * retries, etc. to retrieve the final response body.
 *
 * <h3>What does 'connected' mean?</h3>
 * This class inherits a {@code connected} field from the superclass. That field
 * is <strong>not</strong> used to indicate not whether this URLConnection is
 * currently connected. Instead, it indicates whether a connection has ever been
 * attempted. Once a connection has been attempted, certain properties (request
 * header fields, request method, etc.) are immutable. Test the {@code
 * connection} field on this class for null/non-null to determine if an instance
 * is currently connected to a server.
 */
class HttpURLConnectionImpl extends HttpURLConnection {

	private final int defaultPort;

	private Proxy proxy;

	private final RawHeaders rawRequestHeaders = new RawHeaders();

	private int redirectionCount;
	
	//MIC
	public boolean isHttps;
	//622
	public URL mUrl;
	private HttpHelper httpHelper;
	private volatile HttpWorker wkr_wifi;
	private volatile HttpWorker wkr_mobile;	

	protected IOException httpEngineFailure;
	protected HttpEngine httpEngine;

	public static boolean isVanilla;

	private void getImplementationType() {


		System.out.println("MIC: HttpURLImpl :: getImplementationType() ");

		BufferedReader obj = null;
		try {
			obj = new BufferedReader(new FileReader("/data/local/input.txt"));
			isVanilla = (obj.readLine().equalsIgnoreCase("Y"))?true:false;
			obj.close();
			if(!isVanilla) {
				System.out.println("MIC: HttpURLImpl :: HttpURLConnectionImpl: getImplementationType() -> initializing httpHelper = HttpHelper()");
				httpHelper = new HttpHelper();
			}
		} catch(Exception e) {
			System.out.println("622 - Exception in reading isVanilla parameter from input.txt!!!");
			e.printStackTrace();
		}
	}

	protected HttpURLConnectionImpl(URL url, int port) {
		super(url);


		System.out.println("MIC: HttpURLImpl :: HttpURLConnectionImpl(URL  = "+url.toString()+" , int port = "+Integer.toString(port)+") ");
		defaultPort = port;
		mUrl = url;
		getImplementationType();
	}

	protected HttpURLConnectionImpl(URL url, int port, Proxy proxy) {

		this(url, port);
		this.proxy = proxy;


		System.out.println("MIC: HttpURLImpl :: HttpURLConnectionImpl(URL url, int port, Proxy proxy) ");

		mUrl = url;
		getImplementationType();	
	}

	//622
	public synchronized void setUrl(String newUrl) throws IOException{

		System.out.println("MIC: HttpURLImpl :: setUrl(newURL = "+newUrl+") ");
		URL previousUrl = url;
		url = new URL(previousUrl, newUrl);
		mUrl = url;
	}   


	@Override public final void connect() throws IOException {


		System.out.println("MIC: HttpURLImpl :: connect() ");
		initHttpEngine();
		try {
			httpEngine.sendRequest();
		} catch (IOException e) {
			httpEngineFailure = e;
			throw e;
		}
	}

	@Override public final void disconnect() {


		// Calling disconnect() before a connection exists should have no effect.   


		System.out.println("MIC: HttpURLImpl :: disconnect() ");

		if (httpEngine != null) {
			// We close the response body here instead of in
			// HttpEngine.release because that is called when input
			// has been completely read from the underlying socket.
			// However the response body can be a GZIPInputStream that
			// still has unread data.
			if (httpEngine.hasResponse()) {
				IoUtils.closeQuietly(httpEngine.getResponseBody());
			}
			httpEngine.release(false);
		}
	}

	/**
	 * Returns an input stream from the server in the case of error such as the
	 * requested file (txt, htm, html) is not found on the remote server.
	 */
	@Override public final InputStream getErrorStream() {


		System.out.println("MIC: HttpURLImpl :: getErrorStream()");
		try {
			HttpEngine response = getResponse();
			if (response.hasResponseBody()
					&& response.getResponseCode() >= HTTP_BAD_REQUEST) {
				return response.getResponseBody();
			}
			return null;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Returns the value of the field at {@code position}. Returns null if there
	 * are fewer than {@code position} headers.
	 */
	@Override public final String getHeaderField(int position) {


		System.out.println("MIC: HttpURLImpl :: getHeaderField(position = "+Integer.toString(position)+")");
		try {
			return getResponse().getResponseHeaders().getHeaders().getValue(position);
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Returns the value of the field corresponding to the {@code fieldName}, or
	 * null if there is no such field. If the field has multiple values, the
	 * last value is returned.
	 */
	@Override public final String getHeaderField(String fieldName) {


		System.out.println("MIC: HttpURLImpl :: getHeaderField(fieldName = "+fieldName+")");
		try {
			RawHeaders rawHeaders = getResponse().getResponseHeaders().getHeaders();
			return fieldName == null
					? rawHeaders.getStatusLine()
							: rawHeaders.get(fieldName);
		} catch (IOException e) {
			return null;
		}
	}

	@Override public final String getHeaderFieldKey(int position) {


		System.out.println("MIC: HttpURLImpl :: getHeaderFieldKey(position = "+Integer.toString(position)+")");
		try {
			return getResponse().getResponseHeaders().getHeaders().getFieldName(position);
		} catch (IOException e) {
			return null;
		}
	}

	@Override public final Map<String, List<String>> getHeaderFields() {


		System.out.println("MIC: HttpURLImpl ::  getHeaderFields() ");
		try {
			return getResponse().getResponseHeaders().getHeaders().toMultimap();
		} catch (IOException e) {
			return null;
		}
	}

	@Override public final Map<String, List<String>> getRequestProperties() {


		System.out.println("MIC: HttpURLImpl :: getRequestProperties()");
		if (connected) {
			throw new IllegalStateException(
					"Cannot access request header fields after connection is set");
		}
		return rawRequestHeaders.toMultimap();
	}

	@Override public final InputStream getInputStream() throws IOException {		 
		System.out.println("MIC: HttpURLImpl :: getInputStream() - Started");
		if (!doInput) {
			throw new ProtocolException("This protocol does not support input");
		}
		/*
        try{
        	// Print out request header properties:
            // Request header cannot be retrieved after HttpEngine is initiated
        	for (String key : getRequestProperties().keySet()) {
        	System.err.println("MIC: HttpURLImpl :: Request Property: " + key + "->" + this.getRequestProperty(key));		
        	}
        }catch(Exception e){
        	System.err.println("MIC: HttpURLImpl :: Exception in printing Request property; " +e.getMessage());
        }
		 */
		//if(HttpHelper.logEnable)
		System.out.println("MIC: From HttpURLConnectionImpl.java: Inside getInputStream() Calling getResponse() from within\n");
		HttpEngine response = getResponse();

		try{
			//Print out response header properties
			Map<String, List<String>> respHeaders = getHeaderFields();
			for (String key : respHeaders.keySet()) {
				System.err.println("MIC: HttpURLImpl :: Response Property: " + key + " -> " + respHeaders.get(key));
			}
		}catch(Exception e){
			System.err.println("MIC: HttpURLImpl :: Exception in printing Response property; " +e.getMessage());
		}

		if(isVanilla) {

			System.out.println("MIC: HttpURLImpl :: getInputStream(): isVanilla");
			/*
			 * if the requested file does not exist, throw an exception formerly the
			 * Error page from the server was returned if the requested file was
			 * text/html this has changed to return FileNotFoundException for all
			 * file types
			 */
			if (getResponseCode() >= HTTP_BAD_REQUEST) {
				throw new FileNotFoundException(url.toString());
			}

			InputStream result = response.getResponseBody();
			if (result == null) {
				throw new IOException("No response body exists; responseCode=" + getResponseCode());
			}
			System.out.println("MIC: HttpURLImpl :: getInputStream(): isVanilla -> Returning");
			return result;
		} 
		else {
			System.out.println("MIC: HttpURLImpl :: getInputStream(): not Vanilla");
			if(httpHelper.isGarbled)
				throw new ProtocolException("Too many redirects");
			InputStream result = null;
			try{
				if(HttpHelper.logEnable)
					System.out.println("CSE622 Inside getInputStream -> Calling getStream()");
				result = httpHelper.getStream();
			} catch(Exception e) {
				System.out.println("622 - HttpHelper: getStream() exception !!!");
				e.printStackTrace();
			}
			if (result == null) {
				System.out.println("622 - InputStream is null!!!");
				throw new IOException("No response body exists");//; responseCode=" + getResponseCode());
			}
			System.out.println("MIC: HttpURLImpl :: getInputStream() : not Vanilla-> Returning");
			return result;
		}
	}

	@Override public final OutputStream getOutputStream() throws IOException {


		System.out.println("MIC: HttpURLImpl ::  getOutputStream()");
		connect();

		OutputStream result = httpEngine.getRequestBody();
		if (result == null) {
			throw new ProtocolException("method does not support a request body: " + method);
		} else if (httpEngine.hasResponse()) {
			throw new ProtocolException("cannot write request body after response has been read");
		}

		return result;
	}

	@Override public final Permission getPermission() throws IOException {

		System.out.println("MIC: HttpURLImpl ::  getPermission()");

		String connectToAddress = getConnectToHost() + ":" + getConnectToPort();
		return new SocketPermission(connectToAddress, "connect, resolve");
	}

	private String getConnectToHost() {


		System.out.println("MIC: HttpURLImpl :: getConnectToHost() ");

		return usingProxy()
				? ((InetSocketAddress) proxy.address()).getHostName()
						: getURL().getHost();
	}

	private int getConnectToPort() {


		System.out.println("MIC: HttpURLImpl :: getConnectToPort() ");
		int hostPort = usingProxy()
				? ((InetSocketAddress) proxy.address()).getPort()
						: getURL().getPort();
				return hostPort < 0 ? getDefaultPort() : hostPort;
	}

	@Override public final String getRequestProperty(String field) {


		System.out.println("MIC: HttpURLImpl :: getRequestProperty(field = "+field+") ");
		if (field == null) {
			return null;
		}
		return rawRequestHeaders.get(field);
	}

	private void initHttpEngine() throws IOException {


		System.out.println("MIC: HttpURLImpl :: initHttpEngine() -> Started");
		if (httpEngineFailure != null) {
			System.out.println("MIC: HttpURLImpl :: initHttpEngine() -> httpEngineFailure Exception!!");
			throw httpEngineFailure;
		} else if (httpEngine != null) {
			return;
		}

		connected = true;
		if(isVanilla) {
			try {
				if (doOutput) {
					System.out.println("MIC: HttpURLImpl:: inside initHttpEngine() ->  doOutput = true");
					if (method == HttpEngine.GET) {
						System.out.println("MIC: HttpURLImpl:: inside initHttpEngine() ->  method = "+method);
						// they are requesting a stream to write to. This implies a POST method
						method = HttpEngine.POST;
						System.out.println("MIC: HttpURLImpl:: inside initHttpEngine() ->  set method to "+method);
					} else if (method != HttpEngine.POST && method != HttpEngine.PUT) {
						// If the request method is neither POST nor PUT, then you're not writing
						throw new ProtocolException(method + " does not support writing");
					}
				}

				System.out.println("MIC: HttpURLImpl:: inside initHttpEngine() -> calling newHttpEngine()");
				httpEngine = newHttpEngine(method, rawRequestHeaders, null, null);
			} catch (IOException e) {
				httpEngineFailure = e;
				throw e;
			}
		}
		System.out.println("MIC: HttpURLImpl :: initHttpEngine() -> Ended");
	}

	/**
	 * Create a new HTTP engine. This hook method is non-final so it can be
	 * overridden by HttpsURLConnectionImpl.
	 */
	protected HttpEngine newHttpEngine(String method, RawHeaders requestHeaders,
			HttpConnection connection, RetryableOutputStream requestBody) throws IOException {

		System.out.println("MIC: HttpURLImpl :: newHttpEngine() -> Started");

		System.out.println("MIC: HttpURLImpl :: newHttpEngine() -> Returning : HttpEngine object");
		return new HttpEngine(-1, this, method, requestHeaders, connection, requestBody);
	}

	/**
	 * Aggressively tries to get the final HTTP response, potentially making
	 * many HTTP requests in the process in order to cope with redirects and
	 * authentication.
	 */
	private HttpEngine getResponse() throws IOException {


		System.out.println("MIC: HttpURLImpl :: getResponse() -> Started");
		if(isVanilla) {
			System.out.println("MIC: HttpURLImpl:: getResponse(): isVanilla: Calling initHttpEngine() from within\n");
			initHttpEngine();

			System.out.println("MIC: HttpURLImpl :: getResponse(): isVanilla  -> Checking if httpengine.hasResponse()");
			if (httpEngine.hasResponse()) {
				System.out.println("MIC: HttpURLImpl :: getResponse(): isVanilla  -> Ending1 : return httpEngine");
				return httpEngine;
			}

			while (true) {
				try {
					System.out.println("MIC: HttpURLImpl :: getResponse() : isVanilla : calling httpEngine.sendRequest()");
					httpEngine.sendRequest();
					System.out.println("MIC: HttpURLImpl :: getResponse() : isVanilla : calling httpEngine.readResponse()");
					httpEngine.readResponse();
				} catch (IOException e) {
					/*
					 * If the connection was recycled, its staleness may have caused
					 * the failure. Silently retry with a different connection.
					 */
					System.out.println("MIC: HttpURLImpl :: getResponse() : isVanilla : in catch : retry with a different connection");
					OutputStream requestBody = httpEngine.getRequestBody();
					if (httpEngine.hasRecycledConnection() && (requestBody == null || requestBody instanceof RetryableOutputStream)) {
						httpEngine.release(false);
						httpEngine = newHttpEngine(method, rawRequestHeaders, null,	(RetryableOutputStream) requestBody);
						continue;
					}
					httpEngineFailure = e;
					throw e;
				}

				Retry retry = processResponseHeaders();
				if (retry == Retry.NONE) {
					System.out.println("MIC: HttpURLImpl :: getResponse() : isVanilla -> retry == Retry.NONE");
					httpEngine.automaticallyReleaseConnectionToPool();
					System.out.println("MIC: HttpURLImpl :: getResponse() : isVanilla -> Ending2 : return httpEngine");
					return httpEngine;
				}

				/*
				 * The first request was insufficient. Prepare for another...
				 */
				String retryMethod = method;
				OutputStream requestBody = httpEngine.getRequestBody();

				/*
				 * Although RFC 2616 10.3.2 specifies that a HTTP_MOVED_PERM
				 * redirect should keep the same method, Chrome, Firefox and the
				 * RI all issue GETs when following any redirect.
				 */
				int responseCode = getResponseCode();
				if (responseCode == HTTP_MULT_CHOICE || responseCode == HTTP_MOVED_PERM
						|| responseCode == HTTP_MOVED_TEMP || responseCode == HTTP_SEE_OTHER) {
					System.out.println("MIC: HttpURLImpl :: getResponse() : isVanilla -> Changing retryMethod = GET");
					retryMethod = HttpEngine.GET;
					requestBody = null;
				}

				if (requestBody != null && !(requestBody instanceof RetryableOutputStream)) {
					throw new HttpRetryException("Cannot retry streamed HTTP body",
							httpEngine.getResponseCode());
				}

				if (retry == Retry.DIFFERENT_CONNECTION) {
					httpEngine.automaticallyReleaseConnectionToPool();
				} else {
					httpEngine.markConnectionAsRecycled();
				}

				httpEngine.release(true);

				System.out.println("MIC: HttpURLImpl :: getResponse() : isVanilla -> Continue while loop");
				httpEngine = newHttpEngine(retryMethod, rawRequestHeaders,
						httpEngine.getConnection(), (RetryableOutputStream) requestBody);
			}
		} else {
			System.out.println("MIC: HttpURLImpl:: Inside getInputStream():: Not Vanilla: Calling initHttpEngine() from within\n");
			initHttpEngine();
			try {
				if(HttpHelper.logEnable)
					System.out.println("CSE622: From HttpURLConnImpl - inside getResponse() creating Worker WIFI");
				wkr_wifi = new HttpWorker(this, httpHelper, "wifi", doOutput);
				if(HttpHelper.logEnable)
					System.out.println("CSE622: From HttpURLConnImpl - inside getResponse() creating Worker MOBILE");
				wkr_mobile = new HttpWorker(this, httpHelper, "mobile", doOutput);

				if(HttpHelper.logEnable)
					System.out.println("622 - HttpURLConnectionImpl - Entering JOIN Lock");

				synchronized(HttpHelper.joinLock) {
					HttpHelper.joinLock.wait();
				}
				if(!httpHelper.supportByteRequest) {
					System.out.println("622 - HttpURLConnectionImpl - Single InputStream Case - Waiting for workers to complete");
					wkr_wifi.join();
					wkr_mobile.join();
				}
				else {
					System.out.println("622 - HttpURLConnectionImpl - Buffering Case - Workers continue running");
				}
			} 
			catch(InterruptedException e) {
				System.out.println("622 - Thread JOIN Interrupted!!!");
				e.printStackTrace();
			}
			return httpEngine;

		}
	}

	HttpEngine getHttpEngine() {


		System.out.println("MIC: HttpURLImpl :: getHttpEngine() ");
		return httpEngine;
	}

	enum Retry {
		NONE,
		SAME_CONNECTION,
		DIFFERENT_CONNECTION
	}

	/**
	 * Returns the retry action to take for the current response headers. The
	 * headers, proxy and target URL or this connection may be adjusted to
	 * prepare for a follow up request.
	 */
	private Retry processResponseHeaders() throws IOException {


		System.out.println("MIC: HttpURLImpl :: processResponseHeaders() "); 

		switch (getResponseCode()) {
		case HTTP_PROXY_AUTH:
			if (!usingProxy()) {
				throw new IOException(
						"Received HTTP_PROXY_AUTH (407) code while not using proxy");
			}
			// fall-through
		case HTTP_UNAUTHORIZED:
			boolean credentialsFound = processAuthHeader(getResponseCode(),
					httpEngine.getResponseHeaders(), rawRequestHeaders);
			return credentialsFound ? Retry.SAME_CONNECTION : Retry.NONE;

		case HTTP_MULT_CHOICE:
		case HTTP_MOVED_PERM:
		case HTTP_MOVED_TEMP:
		case HTTP_SEE_OTHER:
			if (!getInstanceFollowRedirects()) {
				return Retry.NONE;
			}
			if (++redirectionCount > HttpEngine.MAX_REDIRECTS) {
				throw new ProtocolException("Too many redirects");
			}
			String location = getHeaderField("Location");
			if (location == null) {
				return Retry.NONE;
			}
			URL previousUrl = url;
			url = new URL(previousUrl, location);
			if (!previousUrl.getProtocol().equals(url.getProtocol())) {
				return Retry.NONE; // the scheme changed; don't retry.
			}
			if (previousUrl.getHost().equals(url.getHost())
					&& previousUrl.getEffectivePort() == url.getEffectivePort()) {
				return Retry.SAME_CONNECTION;
			} else {
				return Retry.DIFFERENT_CONNECTION;
			}

		default:
			return Retry.NONE;
		}
	}

	/**
	 * React to a failed authorization response by looking up new credentials.
	 *
	 * @return true if credentials have been added to successorRequestHeaders
	 *     and another request should be attempted.
	 */
	final boolean processAuthHeader(int responseCode, ResponseHeaders response,
			RawHeaders successorRequestHeaders) throws IOException {


		System.out.println("MIC: HttpURLImpl :: processAuthHeader() "); 

		if (responseCode != HTTP_PROXY_AUTH && responseCode != HTTP_UNAUTHORIZED) {
			throw new IllegalArgumentException("Bad response code: " + responseCode);
		}

		// keep asking for username/password until authorized
		String challengeHeader = responseCode == HTTP_PROXY_AUTH
				? "Proxy-Authenticate"
						: "WWW-Authenticate";
		String credentials = getAuthorizationCredentials(response.getHeaders(), challengeHeader);
		if (credentials == null) {
			return false; // could not find credentials, end request cycle
		}

		// add authorization credentials, bypassing the already-connected check
		String fieldName = responseCode == HTTP_PROXY_AUTH
				? "Proxy-Authorization"
						: "Authorization";
		successorRequestHeaders.set(fieldName, credentials);
		return true;
	}

	/**
	 * Returns the authorization credentials on the base of provided challenge.
	 */
	private String getAuthorizationCredentials(RawHeaders responseHeaders, String challengeHeader)
			throws IOException {


		System.out.println("MIC: HttpURLImpl :: getAuthorizationCredentials() "); 

		List<Challenge> challenges = HeaderParser.parseChallenges(responseHeaders, challengeHeader);
		if (challenges.isEmpty()) {
			throw new IOException("No authentication challenges found");
		}

		for (Challenge challenge : challenges) {
			// use the global authenticator to get the password
			PasswordAuthentication auth = Authenticator.requestPasswordAuthentication(
					getConnectToInetAddress(), getConnectToPort(), url.getProtocol(),
					challenge.realm, challenge.scheme);
			if (auth == null) {
				continue;
			}

			// base64 encode the username and password
			String usernameAndPassword = auth.getUserName() + ":" + new String(auth.getPassword());
			byte[] bytes = usernameAndPassword.getBytes(Charsets.ISO_8859_1);
			String encoded = Base64.encode(bytes);
			return challenge.scheme + " " + encoded;
		}

		return null;
	}

	private InetAddress getConnectToInetAddress() throws IOException {


		System.out.println("MIC: HttpURLImpl :: getConnectToInetAddress() "); 

		return usingProxy()
				? ((InetSocketAddress) proxy.address()).getAddress()
						: InetAddress.getByName(getURL().getHost());
	}

	final int getDefaultPort() {


		System.out.println("MIC: HttpURLImpl :: getDefaultPort() "); 
		return defaultPort;
	}

	/** @see HttpURLConnection#setFixedLengthStreamingMode(int) */
	final int getFixedContentLength() {


		System.out.println("MIC: HttpURLImpl :: getFixedContentLength() "); 

		return fixedContentLength;
	}

	/** @see HttpURLConnection#setChunkedStreamingMode(int) */
	final int getChunkLength() {


		System.out.println("MIC: HttpURLImpl :: getChunkLength()"); 

		return chunkLength;
	}

	final Proxy getProxy() {


		System.out.println("MIC: HttpURLImpl :: getProxy()"); 

		return proxy;
	}

	final void setProxy(Proxy proxy) {


		System.out.println("MIC: HttpURLImpl :: setProxy(Proxy proxy)"); 
		this.proxy = proxy;
	}

	@Override public final boolean usingProxy() {


		System.out.println("MIC: HttpURLImpl :: usingProxy()"); 

		return (proxy != null && proxy.type() != Proxy.Type.DIRECT);
	}

	@Override public String getResponseMessage() throws IOException {


		System.out.println("MIC: HttpURLImpl :: getResponseMessage()"); 

		return getResponse().getResponseHeaders().getHeaders().getResponseMessage();
	}

	@Override public final int getResponseCode() throws IOException {


		System.out.println("MIC: HttpURLImpl :: getResponseCode()"); 

		return getResponse().getResponseCode();
	}

	@Override public final void setRequestProperty(String field, String newValue) {


		System.out.println("MIC: HttpURLImpl :: setRequestProperty(field = "+field+", newValue = "+newValue+")"); 

		if (connected) {
			throw new IllegalStateException("Cannot set request property after connection is made");
		}
		if (field == null) {
			throw new NullPointerException("field == null");
		}
		rawRequestHeaders.set(field, newValue);
	}

	@Override public final void addRequestProperty(String field, String value) {


		System.out.println("MIC: HttpURLImpl :: addRequestProperty(String "+field+", newValue = "+value+")");
		if (connected) {
			throw new IllegalStateException("Cannot add request property after connection is made");
		}
		if (field == null) {
			throw new NullPointerException("field == null");
		}
		rawRequestHeaders.add(field, value);
	}
}
