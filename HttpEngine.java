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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.CookieHandler;
import java.net.ExtendedResponseCache;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ResponseCache;
import java.net.ResponseSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.SSLSocketFactory;
import libcore.io.IoUtils;
import libcore.io.Streams;
import libcore.util.EmptyArray;
import java.text.SimpleDateFormat;


//import android.util.Log;

/**
 * Handles a single HTTP request/response pair. Each HTTP engine follows this
 * lifecycle:
 * <ol>
 *     <li>It is created.
 *     <li>The HTTP request message is sent with sendRequest(). Once the request
 *         is sent it is an error to modify the request headers. After
 *         sendRequest() has been called the request body can be written to if
 *         it exists.
 *     <li>The HTTP response message is read with readResponse(). After the
 *         response has been read the response headers and body can be read.
 *         All responses have a response body input stream, though in some
 *         instances this stream is empty.
 * </ol>
 *
 * <p>The request and response may be served by the HTTP response cache, by the
 * network, or by both in the event of a conditional GET.
 *
 * <p>This class may hold a socket connection that needs to be released or
 * recycled. By default, this socket connection is held when the last byte of
 * the response is consumed. To release the connection when it is no longer
 * required, use {@link #automaticallyReleaseConnectionToPool()}.
 */
public class HttpEngine {
	private static final CacheResponse GATEWAY_TIMEOUT_RESPONSE = new CacheResponse() {
		@Override public Map<String, List<String>> getHeaders() throws IOException {
			Map<String, List<String>> result = new HashMap<String, List<String>>();
			result.put(null, Collections.singletonList("HTTP/1.1 504 Gateway Timeout"));
			return result;
		}
		@Override public InputStream getBody() throws IOException {
			return new ByteArrayInputStream(EmptyArray.BYTE);
		}
	};

	/**
	 * The maximum number of bytes to buffer when sending headers and a request
	 * body. When the headers and body can be sent in a single write, the
	 * request completes sooner. In one WiFi benchmark, using a large enough
	 * buffer sped up some uploads by half.
	 */
	private static final int MAX_REQUEST_BUFFER_LENGTH = 32768;

	public static final int DEFAULT_CHUNK_LENGTH = 1024;

	public static final String OPTIONS = "OPTIONS";
	public static final String GET = "GET";
	public static final String HEAD = "HEAD";
	public static final String POST = "POST";
	public static final String PUT = "PUT";
	public static final String DELETE = "DELETE";
	public static final String TRACE = "TRACE";
	public static final String CONNECT = "CONNECT";

	public static final int HTTP_CONTINUE = 100;

	/**
	 * HTTP 1.1 doesn't specify how many redirects to follow, but HTTP/1.0
	 * recommended 5. http://www.w3.org/Protocols/HTTP/1.0/spec.html#Code3xx
	 */
	public static final int MAX_REDIRECTS = 5;

	protected HttpURLConnectionImpl policy;

	protected final String method;

	private ResponseSource responseSource;

	protected HttpConnection connection;
	private InputStream socketIn;
	private OutputStream socketOut;

	/*
	 * This stream buffers the request headers and the request body when their
	 * combined size is less than MAX_REQUEST_BUFFER_LENGTH. By combining them
	 * we can save socket writes, which in turn saves a packet transmission.
	 * This is socketOut if the request size is large or unknown.
	 */
	private OutputStream requestOut;
	private AbstractHttpOutputStream requestBodyOut;

	private InputStream responseBodyIn;

	private final ResponseCache responseCache = ResponseCache.getDefault();
	private CacheResponse cacheResponse;
	private CacheRequest cacheRequest;

	/** The time when the request headers were written, or -1 if they haven't been written yet. */
	private long sentRequestMillis = -1;

	/**
	 * True if this client added an "Accept-Encoding: gzip" header field and is
	 * therefore responsible for also decompressing the transfer stream.
	 */
	private boolean transparentGzip;

	boolean sendChunked;

	/**
	 * The version this client will use. Either 0 for HTTP/1.0, or 1 for
	 * HTTP/1.1. Upon receiving a non-HTTP/1.1 response, this client
	 * automatically sets its version to HTTP/1.0.
	 */
	// TODO: is HTTP minor version tracked across HttpEngines?
	private int httpMinorVersion = 1; // Assume HTTP/1.1

	private final URI uri;

	private RequestHeaders requestHeaders;

	/** Null until a response is received from the network or the cache */
	private ResponseHeaders responseHeaders;

	/*
	 * The cache response currently being validated on a conditional get. Null
	 * if the cached response doesn't exist or doesn't need validation. If the
	 * conditional get succeeds, these will be used for the response headers and
	 * body. If it fails, these be closed and set to null.
	 */
	private ResponseHeaders cachedResponseHeaders;
	private InputStream cachedResponseBody;

	/**
	 * True if the socket connection should be released to the connection pool
	 * when the response has been fully read.
	 */
	private boolean automaticallyReleaseConnectionToPool;

	/** True if the socket connection is no longer needed by this engine. */
	private boolean connectionReleased;

	//622
	private String requestTimeStamp;
	private long requestTimeStampMillis = 0;
	
	int start, end;

	public String getRequestTimeStamp() {
		System.out.println("MIC : HttpEngine: getRequestTimeStamp()");
		return requestTimeStamp;
	}

	private int TYPE = -1;

	public long getRequestTimeStampMillis() {
		System.out.println("MIC : HttpEngine: getRequestTimeStampMillis()");
		return requestTimeStampMillis;
	}


	/*
	Maintain the time for start and end	
	*/
	private Long startTime;
	
	public Long getStartTime(){
		return startTime;
	}
	/**
	 * @param requestHeaders the client's supplied request headers. This class
	 *     creates a private copy that it can mutate.
	 * @param connection the connection used for an intermediate response
	 *     immediately prior to this request/response pair, such as a same-host
	 *     redirect. This engine assumes ownership of the connection and must
	 *     release it when it is unneeded.
	 */
	public HttpEngine(int TYPE, HttpURLConnectionImpl policy, String method, RawHeaders requestHeaders,
			HttpConnection connection, RetryableOutputStream requestBodyOut) throws IOException {
		this.TYPE = TYPE;
		this.policy = policy;
		this.method = method;
		this.connection = connection;
		this.requestBodyOut = requestBodyOut;
		try {
			uri = policy.getURL().toURILenient();
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
		this.requestHeaders = new RequestHeaders(uri, new RawHeaders(requestHeaders));
		startTime = System.currentTimeMillis();
		//System.out.println("MIC : new HttpEngine(): TYPE = " +Integer.toString(TYPE)+", method = "+method+", RawHeaders = "+requestHeaders);
		System.out.println("MIC : HttpEngine: new HttpEngine(TYPE, policy, method, RawHeaders");
	}

	public URI getUri() {
		System.out.println("MIC : HttpEngine: getUri()");
		return uri;
	}

	private String getCurrentTimeStamp() {
		System.out.println("MIC : HttpEngine: getCurrentTimeStamp()");
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Date now = new Date();
		String strDate = sdfDate.format(now);
		return strDate;
	}


	/**
	 * Figures out what the response source will be, and opens a socket to that
	 * source if necessary. Prepares the request headers and gets ready to start
	 * writing the request body if it exists.
	 */
	public final void sendRequest() throws IOException {
		System.out.println("MIC : HttpEngine: sendRequest(): Header: method = "+this.method);
		requestTimeStamp = getCurrentTimeStamp();
		requestTimeStampMillis = System.currentTimeMillis();
		if (responseSource != null) {
			return;
		}

		prepareRawRequestHeaders();  //////this call eventually results to the call of HttpsURLConnectionImpl's func includeAuthorityInRequestLine()'s invocation after sendRequest() is called 
		initResponseSource();
		if (responseCache instanceof ExtendedResponseCache) {
			((ExtendedResponseCache) responseCache).trackResponse(responseSource);
		}

		/*
		 * The raw response source may require the network, but the request
		 * headers may forbid network use. In that case, dispose of the network
		 * response and use a GATEWAY_TIMEOUT response instead, as specified
		 * by http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.9.4.
		 */
		if (requestHeaders.isOnlyIfCached() && responseSource.requiresConnection()) {
			if (responseSource == ResponseSource.CONDITIONAL_CACHE) {
				IoUtils.closeQuietly(cachedResponseBody);
			}
			this.responseSource = ResponseSource.CACHE;
			this.cacheResponse = GATEWAY_TIMEOUT_RESPONSE;
			RawHeaders rawResponseHeaders = RawHeaders.fromMultimap(cacheResponse.getHeaders());
			setResponse(new ResponseHeaders(uri, rawResponseHeaders), cacheResponse.getBody());
		}

		if (responseSource.requiresConnection()) {////////////////////////
			sendSocketRequest(); //////this call eventually results to the call of HttpsURLConnectionImpl's func connect()'s invocation
		} else if (connection != null) {
			HttpConnectionPool.INSTANCE.recycle(connection, TYPE);
			connection = null;
		}
	}

	/**
	 * Initialize the source for this response. It may be corrected later if the
	 * request headers forbids network use.
	 */
	private void initResponseSource() throws IOException {
		System.out.println("MIC : HttpEngine: initResponseSource()");
		responseSource = ResponseSource.NETWORK;
		if (!policy.getUseCaches() || responseCache == null) {
			return;
		}

		CacheResponse candidate = responseCache.get(uri, method,
				requestHeaders.getHeaders().toMultimap());
		if (candidate == null) {
			return;
		}

		Map<String, List<String>> responseHeadersMap = candidate.getHeaders();
		cachedResponseBody = candidate.getBody();
		if (!acceptCacheResponseType(candidate)
				|| responseHeadersMap == null
				|| cachedResponseBody == null) {
			IoUtils.closeQuietly(cachedResponseBody);
			return;
		}

		RawHeaders rawResponseHeaders = RawHeaders.fromMultimap(responseHeadersMap);
		cachedResponseHeaders = new ResponseHeaders(uri, rawResponseHeaders);
		long now = System.currentTimeMillis();
		this.responseSource = cachedResponseHeaders.chooseResponseSource(now, requestHeaders);
		if (responseSource == ResponseSource.CACHE) {
			System.out.println("MIC : Header: HttpEngine: initResponseSource(): Response Source = CACHE");
			this.cacheResponse = candidate;
			setResponse(cachedResponseHeaders, cachedResponseBody);
		} else if (responseSource == ResponseSource.CONDITIONAL_CACHE) {
			this.cacheResponse = candidate;
			System.out.println("MIC : Header: HttpEngine: initResponseSource(): Response Source = CONDITIONAL_CACHE");
		} else if (responseSource == ResponseSource.NETWORK) {
			System.out.println("MIC : Header: HttpEngine: initResponseSource(): Response Source = NETWORK");
			IoUtils.closeQuietly(cachedResponseBody);
		} else {
			throw new AssertionError();
		}
	}

	private void sendSocketRequest() throws IOException {
		System.out.println("MIC : HttpEngine: sendSocketRequest()");
		//	if (httpHelper.useBoth && (httpHelper.wifiConnection == null || httpHelper.mobileConnection == null)) {
		//	    connect();
		//	}

		if (connection == null) {
			//////if(HttpHelper.logEnable)
			System.out.println("622 - HttpEngine...Sending Request for HttpConnection");
			connect(); ///////////////// HttpsEngine.coneect() is invoked in case of Https
		}

		if (socketOut != null || requestOut != null || socketIn != null) {
			System.out.println("622 - HttpEngine... IllegalState Exception");
			throw new IllegalStateException();
		}

		socketOut = connection.getOutputStream();
		requestOut = socketOut;
		socketIn = connection.getInputStream();

		if (hasRequestBody()) {
			initRequestBodyOut();
		}
	}

	/**
	 * Connect to the origin server either directly or via a proxy.
	 */
	protected void connect() throws IOException {
		System.out.println("MIC : HttpEngine: connect()");
		if (connection == null) {
			System.out.println("MIC : HttpEngine: connect() -> connection = null ... calling openSocketConnection()");
			connection = openSocketConnection();
		}
	}

	protected final HttpConnection openSocketConnection() throws IOException {
		System.out.println("MIC : HttpEngine: openSocketConnection()");
		HttpConnection result;
		int connTimeout, readTimeout;
		if(TYPE==1)
		{
			connTimeout = policy.getWifiConnTimeout();
			readTimeout = policy.getWifiReadTimeout();
		}
		else if(TYPE == 2)
		{
			connTimeout = policy.getMobConnTimeout();
			readTimeout = policy.getMobReadTimeout();
		}
		else
		{
			connTimeout = policy.getConnectTimeout();
			readTimeout = policy.getReadTimeout();
		}	
		
		result = HttpConnection.connect(uri, getSslSocketFactory(), policy.getProxy(), requiresTunnel(), connTimeout, TYPE);  /////////// this leads to HttpConnection
		Proxy proxy = result.getAddress().getProxy();
		if (proxy != null) {
			policy.setProxy(proxy);
		}
		result.setSoTimeout(readTimeout);
		return result;
	}

	protected void initRequestBodyOut() throws IOException {
		System.out.println("MIC : HttpEngine: initRequestBodyOut()");
		int chunkLength = policy.getChunkLength();
		if (chunkLength > 0 || requestHeaders.isChunked()) {
			sendChunked = true;
			if (chunkLength == -1) {
				chunkLength = DEFAULT_CHUNK_LENGTH;
			}
		}

		if (socketOut == null) {
			throw new IllegalStateException("No socket to write to; was a POST cached?");
		}

		if (httpMinorVersion == 0) {
			sendChunked = false;
		}

		int fixedContentLength = policy.getFixedContentLength();
		if (requestBodyOut != null) {
			// request body was already initialized by the predecessor HTTP engine
		} else if (fixedContentLength != -1) {
			writeRequestHeaders(fixedContentLength);
			requestBodyOut = new FixedLengthOutputStream(requestOut, fixedContentLength);
		} else if (sendChunked) {
			writeRequestHeaders(-1);
			requestBodyOut = new ChunkedOutputStream(requestOut, chunkLength);
		} else if (requestHeaders.getContentLength() != -1) {
			writeRequestHeaders(requestHeaders.getContentLength());
			requestBodyOut = new RetryableOutputStream(requestHeaders.getContentLength());
		} else {
			requestBodyOut = new RetryableOutputStream();
		}
	}

	/**
	 * @param body the response body, or null if it doesn't exist or isn't
	 *     available.
	 */
	private void setResponse(ResponseHeaders headers, InputStream body) throws IOException {
		System.out.println("MIC : HttpEngine: setResponse(headers, body)");
		if (this.responseBodyIn != null) {
			throw new IllegalStateException();
		}
		this.responseHeaders = headers;
		this.httpMinorVersion = responseHeaders.getHeaders().getHttpMinorVersion();
		if (body != null) {
			initContentStream(body);
		}
		//printResponseHeaders();
	}

	private boolean hasRequestBody() {
		System.out.println("MIC : HttpEngine: hasRequestBody()");
		return method == POST || method == PUT;
	}

	/**
	 * Returns the request body or null if this request doesn't have a body.
	 */
	public final OutputStream getRequestBody() {
		System.out.println("MIC : HttpEngine: getRequestBody()");
		if (responseSource == null) {
			throw new IllegalStateException();
		}
		return requestBodyOut;
	}

	public final boolean hasResponse() {
		System.out.println("MIC : HttpEngine: hasResponse()");
		return responseHeaders != null;
	}

	public final RequestHeaders getRequestHeaders() {
		System.out.println("MIC : HttpEngine: getRequestHeaders()");
		return requestHeaders;
	}

	public final ResponseHeaders getResponseHeaders() {
		System.out.println("MIC : HttpEngine: getResponseHeaders()");
		if (responseHeaders == null) {
			throw new IllegalStateException();
		}
		return responseHeaders;
	}

	public final int getResponseCode() {
		System.out.println("MIC : HttpEngine: getResponseCode()");
		if (responseHeaders == null) {
			throw new IllegalStateException();
		}
		return responseHeaders.getHeaders().getResponseCode();
	}

	public final InputStream getResponseBody() {
		System.out.println("MIC : HttpEngine: getResponseBody()");
		if (responseHeaders == null) {
			throw new IllegalStateException();
		}
		return responseBodyIn;
	}

	public final CacheResponse getCacheResponse() {
		System.out.println("MIC : HttpEngine: getCacheResponse()");
		return cacheResponse;
	}

	public final HttpConnection getConnection() {
		System.out.println("MIC : HttpEngine: getConnection()");
		return connection;
	}

	public final boolean hasRecycledConnection() {
		System.out.println("MIC : HttpEngine: hasRecycledConnection()");
		return connection != null && connection.isRecycled();
	}

	/**
	 * Returns true if {@code cacheResponse} is of the right type. This
	 * condition is necessary but not sufficient for the cached response to
	 * be used.
	 */
	protected boolean acceptCacheResponseType(CacheResponse cacheResponse) {
		System.out.println("MIC : HttpEngine: acceptCacheResponseType()");
		return true;
	}

	private void maybeCache() throws IOException {
		System.out.println("MIC : HttpEngine: maybeCache()");
		// Never cache responses to proxy CONNECT requests.
		if (method == CONNECT) {
			return;
		}

		// Are we caching at all?
		if (!policy.getUseCaches() || responseCache == null) {
			return;
		}

		// Should we cache this response for this request?
		if (!responseHeaders.isCacheable(requestHeaders)) {
			return;
		}

		// Offer this request to the cache.
		cacheRequest = responseCache.put(uri, getHttpConnectionToCache());
	}

	protected HttpURLConnection getHttpConnectionToCache() {
		System.out.println("MIC : HttpEngine: getHttpConnectionToCache()");
		return policy;
	}

	public void closeConnection() {
		System.out.println("MIC : HttpEngine: closeConnection()");
		if(connection != null) {
			connection.closeSocketAndStreams();
			connection = null;
		}
	}

	public void forceRelease() {
		System.out.println("MIC : HttpEngine: forceRelease()");
		automaticallyReleaseConnectionToPool = true;
		connectionReleased = true;
		if(connection != null) {
			HttpConnectionPool.INSTANCE.recycle(connection, TYPE);
		} else {
			System.out.println("622 - HttpEngine - forceRelease() - connection is NULL!!!");
		}
	}

	public void hasConnectionClosed() {
		System.out.println("MIC : HttpEngine: hasConnectionClosed()");
		if (responseHeaders == null) {
			System.out.println("622 - HttpEngine - checkForConnectionClose() - responseHeaders are NULL...closing the underlying connection anyways !!!");
			if(connection != null) {
				connection.closeSocketAndStreams();
				connection = null;
			}
			//throw new Exception("responseHeaders == null");
		}
		else if (responseHeaders != null && responseHeaders.hasConnectionClose()) {
			System.out.println("622 - HttpEngine - isConnectionClosed() - Server wants Connection CLOSED");
			connection.closeSocketAndStreams();
			connection = null;
		}
	}
	
	/**
	 * Cause the socket connection to be released to the connection pool when
	 * it is no longer needed. If it is already unneeded, it will be pooled
	 * immediately.
	 */


	public final void automaticallyReleaseConnectionToPool() {
		System.out.println("MIC : HttpEngine: automaticallyReleaseConnectionToPool()");
		automaticallyReleaseConnectionToPool = true;
		if (connection != null && connectionReleased) {
			HttpConnectionPool.INSTANCE.recycle(connection, TYPE);
			connection = null;
		}
	}

	public final void markConnectionAsRecycled() {
		System.out.println("MIC : HttpEngine: markConnectionAsRecycled()");
		if (connection != null) {
			connection.setRecycled();
		}
	}

	/**
	 * Releases this engine so that its resources may be either reused or
	 * closed.
	 */
	public final void release(boolean reusable) {
		System.out.println("MIC : HttpEngine: release("+Boolean.toString(reusable)+")");
		// If the response body comes from the cache, close it.
		if (responseBodyIn == cachedResponseBody) {
			IoUtils.closeQuietly(responseBodyIn);
		}

		if (!connectionReleased && connection != null) {
			connectionReleased = true;
			//////if(HttpHelper.logEnable)
			//	System.out.println("622 - HttpEngine() - release() - connectionReleased set to TRUE...");
			// We cannot reuse sockets that have incomplete output.
			if (requestBodyOut != null && !requestBodyOut.closed) {
				reusable = false;
			}

			// If the request specified that the connection shouldn't be reused,
			// don't reuse it. This advice doesn't apply to CONNECT requests because
			// the "Connection: close" header goes the origin server, not the proxy.
			if (requestHeaders.hasConnectionClose() && method != CONNECT) {
				reusable = false;
				//////if(HttpHelper.logEnable)
				//	System.out.println("622 - HttpEngine - release() - REQUEST specified that the connection shouldn't be reused");
			}

			// If the response specified that the connection shouldn't be reused, don't reuse it.
			if (responseHeaders != null && responseHeaders.hasConnectionClose()) {
				reusable = false;
				//////if(HttpHelper.logEnable)
				//	System.out.println("622 - HttpEngine - release() - RESPONSE specified that the connection shouldn't be reused");
			}

			if (responseBodyIn instanceof UnknownLengthHttpInputStream) {
				reusable = false;
			}

			if (reusable && responseBodyIn != null) {
				// We must discard the response body before the connection can be reused.
				try {
					Streams.skipAll(responseBodyIn);
				} catch (IOException e) {
					reusable = false;
					System.out.println("622 - HttpEngine - release() - Exception Discarding previous response body!!!");
					e.printStackTrace();
				}
			}

			if (!reusable) {
				connection.closeSocketAndStreams();
				connection = null;
			} else if (automaticallyReleaseConnectionToPool) {
				HttpConnectionPool.INSTANCE.recycle(connection, TYPE);
				connection = null;
			}
		}
	}

	private void initContentStream(InputStream transferStream) throws IOException {
		System.out.println("MIC : HttpEngine: initContentStream(InputStream transferStream)");
		if (transparentGzip && responseHeaders.isContentEncodingGzip()) {
			/*
			 * If the response was transparently gzipped, remove the gzip header field
			 * so clients don't double decompress. http://b/3009828
			 *
			 * Also remove the Content-Length in this case because it contains the length
			 * of the gzipped response. This isn't terribly useful and is dangerous because
			 * clients can query the content length, but not the content encoding.
			 */
			responseHeaders.stripContentEncoding();
			responseHeaders.stripContentLength();
			responseBodyIn = new GZIPInputStream(transferStream);
		} else {
			responseBodyIn = transferStream;
		}
	}

	private InputStream getTransferStream() throws IOException {
		System.out.println("MIC : HttpEngine: getTransferStream()");
		if (!hasResponseBody()) {
			//////if(HttpHelper.logEnable)
			System.out.println("CSE622: From HttpEngine.java: Inside getTransferStream() - calling FixedLengthInputStream(..., ..., ..., length = 0)");
			return new FixedLengthInputStream(socketIn, cacheRequest, this, 0);
		}

		if (responseHeaders.isChunked()) {
			//////if(HttpHelper.logEnable)
			System.out.println("CSE622: From HttpEngine.java: Inside getTransferStream() - calling ChunkedInputStream(...) since httpEngine responseHeaders.isChunked()");
			return new ChunkedInputStream(socketIn, cacheRequest, this);
		}

		if (responseHeaders.getContentLength() != -1) {
			//////if(HttpHelper.logEnable)
			System.out.println("CSE622: From HttpEngine.java: Inside getTransferStream() - calling FixedLengthInputStream(..., ..., ..., resp..headers.GetContLen!=-1");
			return new FixedLengthInputStream(socketIn, cacheRequest, this,
					responseHeaders.getContentLength());
		}

		/*
		 * Wrap the input stream from the HttpConnection (rather than
		 * just returning "socketIn" directly here), so that we can control
		 * its use after the reference escapes.
		 */
		//////if(HttpHelper.logEnable)
		System.out.println("CSE622: From HttpEngine.java: Inside getTransferStream() - calling UnkownLengthHttpInputStream(...) ");
		return new UnknownLengthHttpInputStream(socketIn, cacheRequest, this);
	}

	public void readResponseHeaders() throws IOException {
		System.out.println("MIC : HttpEngine: readResponseHeaders()");
		RawHeaders headers;
		do {
			headers = new RawHeaders();
			headers.setStatusLine(Streams.readAsciiLine(socketIn));
			readHeaders(headers);
		} while (headers.getResponseCode() == HTTP_CONTINUE);
		setResponse(new ResponseHeaders(uri, headers), null);
	}

	/**
	 * Returns true if the response must have a (possibly 0-length) body.
	 * See RFC 2616 section 4.3.
	 */
	public final boolean hasResponseBody() {
		System.out.println("MIC : HttpEngine: hasResponseBody()");
		int responseCode = responseHeaders.getHeaders().getResponseCode();

		// HEAD requests never yield a body regardless of the response headers.
		if (method == HEAD) {
			return false;
		}

		if (method != CONNECT
				&& (responseCode < HTTP_CONTINUE || responseCode >= 200)
				&& responseCode != HttpURLConnectionImpl.HTTP_NO_CONTENT
				&& responseCode != HttpURLConnectionImpl.HTTP_NOT_MODIFIED) {
			return true;
		}

		/*
		 * If the Content-Length or Transfer-Encoding headers disagree with the
		 * response code, the response is malformed. For best compatibility, we
		 * honor the headers.
		 */
		if (responseHeaders.getContentLength() != -1 || responseHeaders.isChunked()) {
			return true;
		}

		return false;
	}

	/**
	 * Trailers are headers included after the last chunk of a response encoded
	 * with chunked encoding.
	 */
	final void readTrailers() throws IOException {
		System.out.println("MIC : HttpEngine: readTrailers()");
		readHeaders(responseHeaders.getHeaders());
	}

	private void readHeaders(RawHeaders headers) throws IOException {
		System.out.println("MIC : HttpEngine: readHeaders(RawHeaders)");
		// parse the result headers until the first blank line
		String line;
		while (!(line = Streams.readAsciiLine(socketIn)).isEmpty()) {
			headers.addLine(line);
		}

		CookieHandler cookieHandler = CookieHandler.getDefault();
		if (cookieHandler != null) {
			cookieHandler.put(uri, headers.toMultimap());
		}
	}
	
	
	
	public final boolean setResponseBody(InputStream micResponse) {
		System.out.println("MIC : HttpEngine: setResponseBody()");
		if (micResponse == null) {
			System.out.println("MIC : HttpEngine: setResponseBody(): Error in setting response returned from MIC: Response = null");
			//throw new IllegalStateException();
			return false;
		}
		else try {
			responseBodyIn = micResponse;
			System.out.println("MIC : HttpEngine: setResponseBody(): responseBodyIn set!!");
			return true;
			
		} catch (Exception e) {
			System.out.println("MIC : HttpEngine: setResponseBody(): Error in setting response returned from MIC: "+e.getMessage());
			e.printStackTrace();
			return false;
		}
		
	}
	/**
	 * Prepares the HTTP headers and sends them to the server.
	 *
	 * <p>For streaming requests with a body, headers must be prepared
	 * <strong>before</strong> the output stream has been written to. Otherwise
	 * the body would need to be buffered!
	 *
	 * <p>For non-streaming requests with a body, headers must be prepared
	 * <strong>after</strong> the output stream has been written to and closed.
	 * This ensures that the {@code Content-Length} header field receives the
	 * proper value.
	 *
	 * @param contentLength the number of bytes in the request body, or -1 if
	 *      the request body length is unknown.
	 */
	private void writeRequestHeaders(int contentLength) throws IOException {
		//System.out.println("MIC : HttpEngine: writeRequestHeaders(contentLength = "+Integer.toString(contentLength)+")");
		System.out.println("MIC : HttpEngine: writeRequestHeaders(contentLength)");
		if (sentRequestMillis != -1) {
			throw new IllegalStateException();
		}

		RawHeaders headersToSend = getNetworkRequestHeaders();
		byte[] bytes = headersToSend.toHeaderString().getBytes(Charsets.ISO_8859_1);

		if (contentLength != -1 && bytes.length + contentLength <= MAX_REQUEST_BUFFER_LENGTH) {
			requestOut = new BufferedOutputStream(socketOut, bytes.length + contentLength);
		}

		sentRequestMillis = System.currentTimeMillis();
		requestOut.write(bytes);
	}

	/**
	 * Returns the headers to send on a network request.
	 *
	 * <p>This adds the content length and content-type headers, which are
	 * neither needed nor known when querying the response cache.
	 *
	 * <p>It updates the status line, which may need to be fully qualified if
	 * the connection is using a proxy.
	 */
	protected RawHeaders getNetworkRequestHeaders() throws IOException {
		System.out.println("MIC : HttpEngine: getNetworkRequestHeaders()");
		requestHeaders.getHeaders().setStatusLine(getRequestLine());

		int fixedContentLength = policy.getFixedContentLength();
		if (fixedContentLength != -1) {
			requestHeaders.setContentLength(fixedContentLength);
		} else if (sendChunked) {
			requestHeaders.setChunked();
		} else if (requestBodyOut instanceof RetryableOutputStream) {
			int contentLength = ((RetryableOutputStream) requestBodyOut).contentLength();
			requestHeaders.setContentLength(contentLength);
		}

		return requestHeaders.getHeaders();
	}

	/**
	 * Populates requestHeaders with defaults and cookies.
	 *
	 * <p>This client doesn't specify a default {@code Accept} header because it
	 * doesn't know what content types the application is interested in.
	 */
	private void prepareRawRequestHeaders() throws IOException {
		System.out.println("MIC : HttpEngine: prepareRawRequestHeaders()");
		requestHeaders.getHeaders().setStatusLine(getRequestLine());

		if (requestHeaders.getUserAgent() == null) {
			requestHeaders.setUserAgent(getDefaultUserAgent());
		}

		if (requestHeaders.getHost() == null) {
			requestHeaders.setHost(getOriginAddress(policy.getURL()));
		}

		if (httpMinorVersion > 0 && requestHeaders.getConnection() == null) {
			requestHeaders.setConnection("Keep-Alive");
		}

		if (requestHeaders.getAcceptEncoding() == null) {
			transparentGzip = true;
			requestHeaders.setAcceptEncoding("gzip");
		}

		if (hasRequestBody() && requestHeaders.getContentType() == null) {
			requestHeaders.setContentType("application/x-www-form-urlencoded");
		}

		long ifModifiedSince = policy.getIfModifiedSince();
		if (ifModifiedSince != 0) {
			requestHeaders.setIfModifiedSince(new Date(ifModifiedSince));
		}

		CookieHandler cookieHandler = CookieHandler.getDefault();
		if (cookieHandler != null) {
			requestHeaders.addCookies(
					cookieHandler.get(uri, requestHeaders.getHeaders().toMultimap()));
		}

		printRequestHeaders();		
	}

	public void printRequestHeaders() {
		//System.out.println("MIC : HttpEngine: Header: Request Headers: --------------------------------------------------------");
		requestHeaders.printHeaders();
		//System.out.println("MIC : HttpEngine: Header: Request Headers: --------------------------------------------------------");
	}

	public void printResponseHeaders() {
		//System.out.println("MIC : HttpEngine: Header: Response Headers: ======================================================");
		responseHeaders.printHeaders();
		//System.out.println("MIC : HttpEngine: Header: Response Headers: ======================================================");
	}

	private String getRequestLine() {
		System.out.println("MIC : HttpEngine: getRequestLine()");
		String protocol = (httpMinorVersion == 0) ? "HTTP/1.0" : "HTTP/1.1";
		return method + " " + requestString() + " " + protocol;
	}

	private String requestString() {
		System.out.println("MIC : HttpEngine: requestString()");
		URL url = policy.getURL();
		if (includeAuthorityInRequestLine()) {
			return url.toString();
		} else {
			String fileOnly = url.getFile();
			if (fileOnly == null) {
				fileOnly = "/";
			} else if (!fileOnly.startsWith("/")) {
				fileOnly = "/" + fileOnly;
			}
			return fileOnly;
		}
	}

	/**
	 * Returns true if the request line should contain the full URL with host
	 * and port (like "GET http://android.com/foo HTTP/1.1") or only the path
	 * (like "GET /foo HTTP/1.1").
	 *
	 * <p>This is non-final because for HTTPS it's never necessary to supply the
	 * full URL, even if a proxy is in use.
	 */
	protected boolean includeAuthorityInRequestLine() {
		System.out.println("MIC : HttpEngine:: includeAuthorityInRequestLine()");
		return policy.usingProxy();
	}

	/**
	 * Returns the SSL configuration for connections created by this engine.
	 * We cannot reuse HTTPS connections if the socket factory has changed.
	 */
	protected SSLSocketFactory getSslSocketFactory() {
		System.out.println("MIC : HttpEngine:: getSslSocketFactory()");
		return null;
	}

	protected final String getDefaultUserAgent() {
		System.out.println("MIC : HttpEngine:: getDefaultUserAgent()");
		String agent = System.getProperty("http.agent");
		return agent != null ? agent : ("Java" + System.getProperty("java.version"));
	}

	protected final String getOriginAddress(URL url) {
		//System.out.println("MIC : HttpEngine:: getOriginAddress(url = "+url.toString()+")");
		System.out.println("MIC : HttpEngine:: getOriginAddress(url)");
		int port = url.getPort();
		String result = url.getHost();
		if (port > 0 && port != policy.getDefaultPort()) {
			result = result + ":" + port;
		}
		return result;
	}

	protected boolean requiresTunnel() {
		return false;
	}

	/**
	 * Flushes the remaining request header and body, parses the HTTP response
	 * headers and starts reading the HTTP response body if it exists.
	 */
	public final void readResponse() throws IOException {
		System.out.println("MIC : HttpEngine:: readResponse()");
		if (responseSource == null) {
			throw new IllegalStateException("readResponse() without sendRequest()");
		}

		if (!responseSource.requiresConnection()) {
			return;
		}

		if (sentRequestMillis == -1) {
			int contentLength = requestBodyOut instanceof RetryableOutputStream
					? ((RetryableOutputStream) requestBodyOut).contentLength()
							: -1;
					try{
						System.out.println("MIC : HttpEngine:: readResponse() :: contentLength = "+Integer.toString(contentLength));
					}
					catch(Exception e){
						System.err.println("MIC : HttpEngine:: readResponse() :: Error in printing contentLength: "+e);            	
					}
					writeRequestHeaders(contentLength);		// this func leads to the call of includeAuthorityInRequestLine() after readResponse() is called
		}

		if (requestBodyOut != null) {
			System.err.println("MIC : HttpEngine:: readResponse() -> requestBodyOut != null)");
			System.err.println("MIC : HttpEngine:: readResponse() -> calling requestBodyOut.close()");
			requestBodyOut.close();
			if (requestBodyOut instanceof RetryableOutputStream) {
				System.err.println("MIC : HttpEngine:: readResponse() -> calling requestBodyOut.writeToSocket(requestOut)");

				((RetryableOutputStream) requestBodyOut).writeToSocket(requestOut);
			}
		}

		requestOut.flush();
		requestOut = socketOut;
		System.err.println("MIC : HttpEngine:: readResponse() -> calling readResponseHeaders()");
		readResponseHeaders();
		responseHeaders.setLocalTimestamps(sentRequestMillis, System.currentTimeMillis());

		if (responseSource == ResponseSource.CONDITIONAL_CACHE) {
			if (cachedResponseHeaders.validate(responseHeaders)) {
				release(true);
				ResponseHeaders combinedHeaders = cachedResponseHeaders.combine(responseHeaders);
				setResponse(combinedHeaders, cachedResponseBody);
				if (responseCache instanceof ExtendedResponseCache) {
					ExtendedResponseCache httpResponseCache = (ExtendedResponseCache) responseCache;
					httpResponseCache.trackConditionalCacheHit();
					httpResponseCache.update(cacheResponse, getHttpConnectionToCache());
				}
				return;
			} else {
				IoUtils.closeQuietly(cachedResponseBody);
			}
		}

		if (hasResponseBody()) {
			maybeCache(); // reentrant. this calls into user code which may call back into this!
		}
		initContentStream(getTransferStream());
	}

	public String getChunkMarkings() {
		return this.responseHeaders.getChunkMarkings();
	}

	public String getRequestChunkMarkings() {
		return this.requestHeaders.getChunkMarkers();
	}

	public int getDownloadSize() {
		return this.responseHeaders.getDownloadSize();
	}
	
	public boolean acceptRanges() {		
		return responseHeaders.supportsRanges();		
	}

	public int contentLength() {
		// TODO Auto-generated method stub
		return this.responseHeaders.getContentLength();
	}
}