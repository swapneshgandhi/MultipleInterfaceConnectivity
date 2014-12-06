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

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import libcore.io.IoUtils;
import libcore.util.Objects;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
//import java.io.FileDescriptor;
import java.io.File;
import libcore.io.IoUtils;
import libcore.io.Libcore;
import static libcore.io.OsConstants.*;

/**

 */
public class HttpHelper {

	//public static InetSocketAddress interfaceIPAddr_wifi = null;
	//public static InetSocketAddress interfaceIPAddr_mobile = null;

	public static int connections_wifi;
	public static int connections_mobile;

	public static boolean WIFI;
	public static boolean MOBILE;
	
	public Socket wifiSocket;
	public Socket mobileSocket;
	public static final int CHUNKSIZE_MIN = 1024;
	private int chunkSize = 102400;
	private int w_chunkSize;
	private int m_chunkSize;
	private int marker;
	public volatile boolean isComplete;
	
	public boolean isGarbled; /* For redirects exceeding HttpEngine.MAX_REDIRECTS */

	public boolean supportByteRequest;
	//private LinkedList<Integer> missingChunks;
	private TreeMap<Integer, Integer> missingChunks;
	//private HashSet<Integer> missingChunkCheck;

	public Object lock;
	/* Minimum value should be 4, 2 buckets for each worker and 2 for the SPECIAL case */
	/* chunksize:Buffer ratio = 1:1, 1:10, 1:50, 1:100 */
	/* #buckets = 4, 12, 52, 102 */
	private int bufferSize; 
	private InputStream noByteRequestSupport = null;
	public ByteArrayBackedInputStream bArrInpStream = null;
	
	public static Object joinLock = new Object();

	public static Object capacityLock = new Object();

	public Object workerLock = new Object();
	
	private class Stats {
		public String timeStamp;
		public long timeTakenIncSocketCreation;
		public int length;
		public int type;
		public long reqTimeMillis;

		public Stats(int length, String currTime, long timeWithSocket, int type, long reqTimeMillis) {
			this.length = length;
			timeStamp = currTime;
			timeTakenIncSocketCreation = timeWithSocket;
			this.type = type;
			this.reqTimeMillis = reqTimeMillis;
		}
	}

	private HashMap<Integer, Stats> statistics;
	private HashMap<Integer, Stats> failedStatistics;
	private HashMap<Integer, Stats> discardedStatistics;

	public static final int M_NORMAL = 1;
	public static final int M_DISCARDED = 2;
	public static final int M_FAILED = 2;

	public static int Throttle;
	
	public static boolean logEnable;
	
	public boolean runConnStatusThread;

	public static final int FIX_SZ = 1;
	public static final int VAR_SZ = 2;

	public static int IMPL_TYPE;

	private ConnectionStatus connStatus;// new ConnectionStatus(httpHelper);
	
	public static int VAR_IMPL_TYPE;

	public static final int T_AIMD = 1;
	public static final int T_TIMED = 2;

	public static int VAR_IMPL_VAL;

	public static int SMOOTHING_WNDW_SZ;

	public static int outstandingRequests;
	/*
	private int lastChunkStart;
	private int workersDone = 0;
	public synchronized void insertLastChunkStart(int start, String type) throws InterruptedException{
		workersDone++;
		if(start != -1)
			lastChunkStart = start;
		System.out.println("622 - HttpHelper - insertLastChunkStart() - type = " + type + ", lastChunkStart = " + start);
		if(workersDone == 2) {
			bArrInpStream.write(start, null, -1, 0, "", 0);
		}
	}

	public synchronized int noOfWorkersDone() {
		return workersDone;
	}
	*/
	private boolean got416 = false;

	public synchronized void set416() {
		got416 = true;
	}

	public synchronized boolean got416() {
		return got416;
	}
	
	private int downloadSize = -1;

	public void setDownloadSize(int size) {
		if(downloadSize == -1)
			downloadSize = size;
	}

	public int getDownloadSize() {
		return downloadSize;
	}

	/*
	private int wifiQueueSize = 0;
	public synchronized void modifyWifiQueueSize(int value) {
		wifiQueueSize += value;
	}

	public synchronized int getWifiQueueSize() {
		return wifiQueueSize;
	}

	private int mobileQueueSize = 0;
	public synchronized void modifyMobileQueueSize(int value) {
		mobileQueueSize += value;
	}

	public synchronized int getMobileQueueSize() {
		return mobileQueueSize;
	}
	*/
	
	public static ConcurrentLinkedQueue<Long> newConnection;

	public HttpHelper(){
		/* Chunksizes = 1MB, 0.5MB, 100KB, 1KB */
		//chunkSize = 1048576;
		//w_chunkSize = m_chunkSize = 0;
		System.out.println("MIC: HttpHelper :: HttpHelper() -> Started");
		connections_wifi = connections_mobile = 0;
		BufferedReader obj = null;
		lock = new Object();
		try {
			obj = new BufferedReader(new FileReader("/data/local/input.txt"));
			obj.readLine();
			bufferSize = Integer.parseInt(obj.readLine());
			//chunkSize = Integer.parseInt(obj.readLine());
			Throttle = Integer.parseInt(obj.readLine());
			int log = Integer.parseInt(obj.readLine());
			int disableConnStatusThread = Integer.parseInt(obj.readLine());
			runConnStatusThread = (disableConnStatusThread == 0) ? false:true;
			System.out.println("MIC: HttpHelper :: HttpHelper() -> Calling ConnectionStatus(this, disableConnStatusThread)");
			//connStatus = new ConnectionStatus(this, disableConnStatusThread);
			IMPL_TYPE = (obj.readLine().equalsIgnoreCase("F")) ? FIX_SZ : VAR_SZ;
			if(IMPL_TYPE == FIX_SZ) {
				chunkSize = Integer.parseInt(obj.readLine());
				obj.readLine();
				obj.readLine();
				obj.readLine();
				SMOOTHING_WNDW_SZ = -1;
			} else {
				obj.readLine();
				chunkSize = CHUNKSIZE_MIN;
				VAR_IMPL_TYPE = (obj.readLine().equals("T")) ? T_TIMED : T_AIMD;
				VAR_IMPL_VAL = Integer.parseInt(obj.readLine());
				SMOOTHING_WNDW_SZ = Integer.parseInt(obj.readLine());
			}
			outstandingRequests = Integer.parseInt(obj.readLine());
			w_chunkSize = m_chunkSize = chunkSize;
			logEnable = (log == 1)?true:false;
			System.out.println("622 - BufferSize = " + bufferSize + /*", chunksize set to " + chunkSize +*/ 
								" and throttle val = " + Throttle + " logging = " + logEnable + 
								" runConnStatus = " + runConnStatusThread);
			obj.close();
		} catch(Exception e) {
			System.out.println("622 - Exception in reading buffersize and chunksize from the input file!!!");
			e.printStackTrace();
		}	
		marker = 0;
		isComplete = false;
		isGarbled = false;
		supportByteRequest = true;
		bArrInpStream = new ByteArrayBackedInputStream(this, bufferSize/*, chunkSize*/);
		//missingChunks = new LinkedList<Integer>();
		missingChunks = new TreeMap<Integer, Integer>();
		//missingChunkCheck = new HashSet<Integer>();
		statistics = new HashMap<Integer, Stats>();
		failedStatistics = new HashMap<Integer, Stats>();
		discardedStatistics = new HashMap<Integer, Stats>();
		newConnection = new ConcurrentLinkedQueue<Long>();
		
		System.out.println("MIC: HttpHelper :: HttpHelper() -> Ended");
	}

	public int getBufferSize() {
		return bufferSize;
	}
/*
    public static InetSocketAddress getInterfaceIPAddr(int type) {
        System.out.println("622 - Inside HttpHelper getInterfaceIPAddr() function");
		if(type == 1 && HttpHelper.interfaceIPAddr_wifi != null) {
            return HttpHelper.interfaceIPAddr_wifi;
        } else if(type == 2 && HttpHelper.interfaceIPAddr_mobile != null) {
            return HttpHelper.interfaceIPAddr_mobile;
        }   
        //FileDescriptor fd = null;
		System.out.println("622 - Inside HttpHelper getInterfaceIPAddr() function - Entering Loop");
        String localAddress = null;
        InetSocketAddress localSockAddress = null;
		while(localAddress != null) {
		try {
            //fd = Libcore.os.socket(AF_INET, SOCK_DGRAM, 0); 
            localAddress = ConnectionStatus.getIp(type);//Libcore.os.ioctlInetAddress(fd, SIOCGIFADDR, "wlan0");
                //////if(HttpHelper.logEnable)
			System.out.println("622: TYPE = " + type + " ...Using IP address: " + localAddress );
            if(localAddress != null) {
				localSockAddress = new InetSocketAddress(localAddress, 0); 
            	if (type == 1) {
                	HttpHelper.interfaceIPAddr_wifi = localSockAddress;
            	}   
            	else {
                	HttpHelper.interfaceIPAddr_mobile = localSockAddress;
            	}   
			}
        } catch (Exception e) {
                 System.out.println("622 - HttpHelper:" + "...Exception in getInterfaceIPAddr()");
                 e.printStackTrace();
        }
    	}
        return localSockAddress;
}
*/

	public void dumpStatistics(int wifiBytes, int mobileBytes, int total) {
		File file = new File("/data/local/stats.txt");
		if(!file.exists())
			try {
				file.createNewFile();
			} catch (IOException e1) {
				System.out.println("622 - Unable to create file!!!");
				e1.printStackTrace();
			}

		PrintWriter writer = null;
		int wifiDownloads = 0;
		int mobileDownloads = 0;
		long timeLostFailed = 0;
		long timeLostDiscarded = 0;
		
		try {
			writer = new PrintWriter(file, "UTF-8");
			
		//////if(HttpHelper.logEnable)
		//	System.out.println("622 - **************STATISTICS********************");
		//int start = 0;
		//long totalTime = 0;
		//long wifiTime = 0;
		//long mobileTime = 0;
		String header = String.format("ChunkStart\tLength\tRequest_TimeStamp\tTime_taken(ms)\tType\tRequestTimeMillis");
		writer.println(header);
		for(Integer chunkStart : statistics.keySet()) {
			Stats obj = statistics.get(chunkStart);
			
			String type = (obj.type == 1)?"WIFI":"MOBILE";
			//String content = "Start = " + start + " || TimeStamp = " + obj.timeStamp + 
			//					" || Time(ms) W/ Socket = " + obj.timeTakenIncSocketCreation + 
			//					" || Time(ms) W/O Socket = " + obj.timeTakenWithoutIncSocketCreation + " || Type = " + type;  
			String content = String.format("%d\t%d\t%s\t%d\t%s\t%d", chunkStart, obj.length, obj.timeStamp, 
											obj.timeTakenIncSocketCreation, type, obj.reqTimeMillis);
			writer.println(content);
			if(obj.type == 1) {
				//wifiTime += time;
				wifiDownloads++;
			}
			else {
				//mobileTime += time;
				mobileDownloads++;
			}
			//start += chunkSize;
		}

		writer.println("*******End********");
		/*
		if(discardedStatistics.size() > 0) {
			writer.println("** Begin Discarded Chunk Stats **");
			writer.println(header);
			for(Integer i : discardedStatistics.keySet()) {
				Stats obj = discardedStatistics.get(i);
				String type = (obj.type == 1)?"WIFI":"MOBILE";
				String content = String.format("%d\t%s\t%d\t%d\t%s", i, obj.timeStamp, obj.timeTakenIncSocketCreation,
												obj.timeTakenWithoutIncSocketCreation, type);
				writer.println(content);
			}
		}
		*/
		if(failedStatistics.size() > 0) {
			//writer.println("*******End********");
			writer.println("** Begin Failed Chunk Stats **");
			writer.println(header);
			for(Integer chunkStart: failedStatistics.keySet()) {
				Stats obj = failedStatistics.get(chunkStart);
				String type = (obj.type == 1)?"WIFI":"MOBILE";
				String content = String.format("%d\t%d\t%s\t%d\t%s\t%d", chunkStart, obj.length, obj.timeStamp, 
												obj.timeTakenIncSocketCreation, type, obj.reqTimeMillis);
				writer.println(content);
				timeLostFailed += obj.timeTakenIncSocketCreation;
			}
		}
		
		if(discardedStatistics.size() > 0) {
			//writer.println("*******End********");
			writer.println("** Begin Discarded Chunk Stats **");
			writer.println(header);
			for(Integer chunkStart: discardedStatistics.keySet()) {
				Stats obj = discardedStatistics.get(chunkStart);
				String type = (obj.type == 1)?"WIFI":"MOBILE";
				String content = String.format("%d\t%d\t%s\t%d\t%s\t%d", chunkStart, obj.length, obj.timeStamp, 
												obj.timeTakenIncSocketCreation, type, obj.reqTimeMillis);
				writer.println(content);
				timeLostDiscarded += obj.timeTakenIncSocketCreation;
			}
		}

		if(!newConnection.isEmpty()) {
			writer.println("**Begin new connection timestamps**");
			for(long l : newConnection) {
				writer.println(l);
			}
		}

        } catch (FileNotFoundException e) {
            System.out.println("622 - File not found exception!!!");
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            System.out.println("622 - Unsupported encoding!!!");
            e.printStackTrace();
        } finally {
			writer.close();
		}  

		//////if(HttpHelper.logEnable) {
			System.out.println("622 - *****************************Results*******************************");
			System.out.println("622: Wifi: #chunks = " + wifiDownloads + " || #bytes = " + wifiBytes);
			System.out.println("622: Mobile: #chunks = " + mobileDownloads + " || #bytes = " + mobileBytes);
			System.out.println("622: Downloaded total " + total + " bytes");
			System.out.println("622: No. of WIFI connections = " + connections_wifi);
			System.out.println("622: No. of Mobile connections = " + connections_mobile);
			System.out.println("622: No. of Failed Chunks = " + failedStatistics.size() + " and time = " + timeLostFailed);
			System.out.println("622: No. of Discarded Chunks = " + discardedStatistics.size() + " and time = " + 
								timeLostDiscarded);
		//}
	}

	public synchronized void insertStatistics(int chunkStart, int length, String currTime, long timeWithSocket, 
											  int interfaceType, int mode, long reqTimeMillis) {
		Stats obj = new Stats(length, currTime, timeWithSocket, interfaceType, reqTimeMillis);
		if (mode == M_NORMAL)
			statistics.put(chunkStart, obj);
		else if (mode == M_FAILED)
			failedStatistics.put(chunkStart, obj);
		else if (mode == M_DISCARDED)
			discardedStatistics.put(chunkStart, obj);
	}
	
	
	public int getChunkSize() {
		return this.chunkSize;
	}

	public synchronized int w_getChunkSize() {
		return this.w_chunkSize;
	}

	public synchronized void w_setChunkSize(int size) {
		this.w_chunkSize = size;
	}

	public synchronized int m_getChunkSize() {
		return this.m_chunkSize;
	}

	public synchronized void m_setChunkSize(int size) {
		this.m_chunkSize = size;
	}

	public int getMarker() {
		return this.marker;
	}
	
	public void setMarker(int value) {
		this.marker = value;
	}

	/* Returns the consolidated stream */
	public InputStream getStream() throws Exception{
		
		if(!supportByteRequest) {
			////if(HttpHelper.logEnable)
				System.out.println("622 - getStream(): NO Support for BYTE RANGE REQUESTs");
			return noByteRequestSupport;
		}
		else {
			////if(HttpHelper.logEnable)
				System.out.println("622 - getStream(): Buffering Case");
			return bArrInpStream;
		}
		
	}

	
	public synchronized void setInputStream(InputStream value) {
		noByteRequestSupport = value;
	}
	
	public synchronized int missingListSize() {
		return missingChunks.size();
	}

	public synchronized void insertToMissingList(int start, int end) {
		//if(!missingChunkCheck.contains(start)) {
		//	missingChunkCheck.add(start);
			missingChunks.put(start, end);
		//}
	}

	public synchronized Entry<Integer, Integer> getNextMissingChunk() {
		/*
		if(missingChunks.size() != 0) {
			Entry<Integer, Integer> result = null;
			for(Entry<Integer, Integer> e : missingChunks.entrySet()) {
				result = e;
				missingChunks.remove(result.getKey());
				break;	
			}
			return result;	
		}
		return null;
		*/
		return missingChunks.pollFirstEntry();
	}
	
	public static void connectionFor(String network) {
		if(network.equals("wifi")) {
			HttpHelper.WIFI = true;
			HttpHelper.MOBILE = false;
		}
		else if(network.equals("mobile")) {
			HttpHelper.WIFI = false;
			HttpHelper.MOBILE = true;
		}			
	}

}
