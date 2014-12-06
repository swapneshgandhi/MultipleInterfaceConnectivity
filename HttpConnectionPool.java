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

import dalvik.system.SocketTagger;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A pool of HTTP connections. This class exposes its tuning parameters as
 * system properties:
 * <ul>
 *   <li>{@code http.keepAlive} true if HTTP connections should be pooled at
 *       all. Default is true.
 *   <li>{@code http.maxConnections} maximum number of connections to each URI.
 *       Default is 5.
 * </ul>
 *
 * <p>This class <i>doesn't</i> adjust its configuration as system properties
 * are changed. This assumes that the applications that set these parameters do
 * so before making HTTP connections, and that this class is initialized lazily.
 */
final class HttpConnectionPool {

    public static final HttpConnectionPool INSTANCE = new HttpConnectionPool();
	
	private final Object lock = new Object();

    private final int maxConnections;
    private final HashMap<HttpConnection.Address, List<HttpConnection>> connectionPool_W
            = new HashMap<HttpConnection.Address, List<HttpConnection>>();
	
	 private final HashMap<HttpConnection.Address, List<HttpConnection>> connectionPool_M
            = new HashMap<HttpConnection.Address, List<HttpConnection>>();

	private int type;

    private HttpConnectionPool() {
        // CSE 622
		String keepAlive = "true";//System.getProperty("http.keepAlive");
        if (keepAlive != null && !Boolean.parseBoolean(keepAlive)) {
            maxConnections = 0;
            return;
        }

        String maxConnectionsString = System.getProperty("http.maxConnections");
        this.maxConnections = maxConnectionsString != null
                ? Integer.parseInt(maxConnectionsString)
                : 5;
    }

    public HttpConnection get(HttpConnection.Address address, int connectTimeout, int TYPE)
            throws IOException {
		// First try to reuse an existing HTTP connection.
        synchronized (/*connectionPool*/lock) {
            List<HttpConnection> connections = null;
            
            this.type = TYPE;
			if(TYPE == 1)
				connections = connectionPool_W.get(address);
			else if(TYPE == 2)
				connections = connectionPool_M.get(address);

            while (connections != null) {
                HttpConnection connection = connections.remove(connections.size() - 1);
                if (connections.isEmpty()) {
                    if(TYPE == 1)
						connectionPool_W.remove(address);
					else if(TYPE == 2)
						connectionPool_M.remove(address);
                    connections = null;
                }
                if (connection.isEligibleForRecycling()) {
                    // Since Socket is recycled, re-tag before using
					//Socket socket = connection.getSocket();
                    //SocketTagger.get().tag(socket);
                    ////if(HttpHelper.logEnable)
						System.out.println("622 - HttpConnectionPool: Found REUSABLE CONNECTION...");
					return connection;
                }
            }
        }
        /*
         * We couldn't find a reusable connection, so we need to create a new
         * connection. We're careful not to do so while holding a lock!
         */
        ////if(HttpHelper.logEnable)
		//	System.out.println("622 - HttpConnectionPool: No reusable connection...creating a new one");
		return address.connect(connectTimeout, type);
    }

    public void recycle(HttpConnection connection, int TYPE) {
        ////if(HttpHelper.logEnable)
		//	System.out.println("622 - HttpConnectionPool - Inside recycle()...");
		
		/*
		Socket socket = connection.getSocket();
        try {
            SocketTagger.get().untag(socket);
        } catch (SocketException e) {
            // When unable to remove tagging, skip recycling and close
            System.out.println("622 - HttpConnectionPool - recycle() - Unable to untagSocket()");
            e.printStackTrace();
			connection.closeSocketAndStreams();
            return;
        }*/

        if (maxConnections > 0 && connection.isEligibleForRecycling()) {
            HttpConnection.Address address = connection.getAddress();
            synchronized (/*connectionPool*/lock) {
                List<HttpConnection> connections = null; 
				if(TYPE == 1)
					connections = connectionPool_W.get(address);
				else if(TYPE == 2)
					connections = connectionPool_M.get(address);
				else {
        			connection.closeSocketAndStreams();
					return;
				}
                if (connections == null) {
                    connections = new ArrayList<HttpConnection>();
                    if(TYPE == 1)
						connectionPool_W.put(address, connections);
                	else if(TYPE == 2)
						connectionPool_M.put(address, connections);
				}
                if (connections.size() < maxConnections) {
                    connection.setRecycled();
                    connections.add(connection);
                    //if(!HttpURLConnectionImpl.isMic && HttpHelper.logEnable)
						System.out.println("622 - HttpConnectionPool - recycle() - Successful");
					return; // keep the connection open
                }
            }
        }

        // don't close streams while holding a lock!
        connection.closeSocketAndStreams();
    }
}
