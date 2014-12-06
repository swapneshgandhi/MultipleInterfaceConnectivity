package libcore.net.http;

import java.io.InputStream;
import libcore.io.IoUtils;
import libcore.util.Objects;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.net.HttpRetryException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.URL;

import libcore.io.ErrnoException;
import java.util.Map.Entry;
import java.io.FileDescriptor;
import java.io.File;

import libcore.io.IoUtils;
import libcore.io.Libcore;
import libcore.net.http.HttpURLConnectionImpl.Retry;
import libcore.net.http.HttpsURLConnectionImpl.HttpUrlConnectionDelegate;
import static libcore.io.OsConstants.*;

//@TODO - Collect statistics for failed and discarded chunks from here as well as from the Buffer

public class NewWorker extends Thread {



	private HttpURLConnectionImpl impl;
	private String type;
	private int TYPE;
	private String method = HttpEngine.GET;
	private int chunkSize;

	private boolean logEnable;

	private int responseLength;
	private int requestLength;
	private long startTime;
	private long endTime;

	private final Object readerQLock = new Object();
	private final Object senderWait = new Object();
	private final Object readerWait = new Object();
	private final Object calcSpeed = new Object();

	private Object returnLock;

	LinkedList<HttpEngine> readerQueue, retryQueue;
	boolean speedAvl = false;

	boolean isReaderDone = false;
	boolean isSenderDone = false;

	private HttpUrlConnectionDelegate httpSimpl;
	ConnectionStatus connStatus;
	MicHttpEngine micEngine;
	//ChunkInfo chnkInfo;
	ArrayList<ChunkInfo> chunkList = new ArrayList<ChunkInfo>();
	boolean readerQIsEmpty, retryQIsEmpty, senderDone, isDone;
	Speed speedObj;
	NewStatistics stats;
	private static boolean bothInfcUp = true;

	private RequestReader reader;

	public NewWorker(HttpURLConnectionImpl i, ConnectionStatus connStatus, MicHttpEngine micEng, String infcType, NewStatistics stats) {

		this.stats = stats;
		this.connStatus = connStatus;
		micEngine = micEng;
		type = infcType;
		TYPE = (type.equals("wifi"))?1:2;           //1 for Wifi, 2 for Mobile
		impl = i;
		method = impl.getRequestMethod();
		if(!method.equals(HttpEngine.GET))
		{
			System.out.println("Request method = "+impl.getRequestMethod()+"!!! Changing method to GET");
			method = HttpEngine.GET;
		}
		logEnable = true;
		readerQueue = new LinkedList<HttpEngine>();
		retryQueue = new LinkedList<HttpEngine>();
		senderDone = false;
		returnLock = micEng.returnLock;
		requestLength = 0;
		isDone = false;
		speedObj = new Speed();
		reader = new RequestReader();
		start();
	}

	private String getCurrentTimeStamp() {
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Date now = new Date();
		String strDate = sdfDate.format(now);
		return strDate;
	}	

	private class Speed {
		final int MAX = 5;
		private long sum, speed, timeout;
		int currnoelem;
		LinkedList<Long> list = new LinkedList<Long>();

		Speed() {
			sum = 0;
			currnoelem = 0;
		}

		long getSpeed() {
			speed = sum/Math.min(currnoelem, MAX);
			return speed;
		}
		
		long getTimeout() {
			timeout = 1024*1000*100/(speed);
			timeout = Math.max(100, timeout);
			timeout = Math.min(10000, timeout);
			return timeout;
		}

		void updateSpeed(long value) {
			if(currnoelem < MAX) {
				sum += value;
			} else {
				sum = sum - list.removeFirst() + value;
			}
			list.add(value);
			currnoelem++;
		}

	}


	public class RequestReader extends Thread {


		//HttpEngine engine;
		int start, end, respCode;
		IOException httpEngineFailure;

		RequestReader() {

		}

		public void run()
		{

			HttpEngine engine;
			int chunkSz;
			while(true)
			{
				System.out.println("MIC: HttpWorker: <" + type + "> : reqReader : new while() iteration started");

				synchronized (readerQLock)
				{
					if(readerQueue.isEmpty())
					{
						readerQIsEmpty = true;

						if(senderDone)
						{
							synchronized (senderWait)
							{
								System.out.println("MIC: HttpWorker: <" + type + "> reqReader : senderDone = true ... will notify sender now ...");
								senderWait.notify();
							}
							return;
						}
						else
						{			
							try
							{
								readerQLock.wait();
							}
							catch (InterruptedException e)
							{
								System.out.println("MIC: HttpWorker: <" + type + "> reqReader : exception in readerQ.wait() !! : "+e.getMessage());
								e.printStackTrace();
							}	
						}
					}

					readerQIsEmpty = false;
					engine = readerQueue.removeFirst();
				}

				try
				{
					System.out.println("MIC: HttpWorker: <" + type + "> reqReader : calling engine.readResponse()");
					engine.readResponse();
					System.out.println("MIC: HttpWorker: <" + type + "> : reqReader : readResponse() done for chunk: "
							+Integer.toString(engine.start)+" - "+Integer.toString(engine.end));
				}
				catch (IOException e)
				{
					System.out.println("MIC: HttpWorker: <" + type + "> : reqReader : Exception in readResponse() : "+e.getMessage());
					e.printStackTrace();
					System.out.println("MIC: HttpWorker: <" + type + "> : reqReader : Calling setMissingChunk()");
					micEngine.setMissingChunk(engine.start, engine.end);
					continue;
				}				
				System.out.println("MIC: HttpWorker: <" + type + "> : reqReader : Printing Response Header:");
				engine.printResponseHeaders();
				respCode = engine.getResponseCode();
				int contentLength = engine.contentLength();
				switch(respCode)
				{
				case(206):
					System.out.println("MIC: NewWorker: <" + type + "> : reqReader : Data downloaded for Chunk: "
							+ Integer.toString(engine.start)+" - "+ Integer.toString(engine.end)+" .. calculating Speed now...");
				try
				{
					synchronized(calcSpeed)       //call speed calculation in this block
					{
						Long endTime = System.currentTimeMillis();
						Long timeDiff = endTime - engine.getStartTime();
						chunkSz = engine.end - engine.start + 1;
						if(contentLength!=chunkSz)
						{
							System.out.println("MIC: NewWorker: <" + type + "> : reqReader : downloaded contentLength "+contentLength+" != "+chunkSz+" calculated chunkSz");
						}
						else
						{
							System.out.println("MIC: NewWorker: <" + type + "> : reqReader : downloaded contentLength "+contentLength+" = "+chunkSz+" calculated chunkSz");
						}
						speedObj.updateSpeed(chunkSz/timeDiff);
						speedAvl = true;
					}
					System.out.println("MIC: NewWorker: <" + type + "> : reqReader : Calling setChunk for chunk: "+engine.start+" - "+engine.end);				

					micEngine.setChunk(engine.start, chunkSz, engine.getResponseBody(), TYPE);
					stats.addChunktoStats(getCurrentTimeStamp(),engine.start, 0, TYPE);
					responseLength = responseLength + chunkSz;
					engine.automaticallyReleaseConnectionToPool();
				}
				catch(Exception e)
				{
					System.out.println("MIC: NewWorker: <" + type + "> : reqReader: Exception: "+e.getMessage());
					e.printStackTrace();
				}

				break;

				default:					
					System.out.println("MIC: Worker: <" + type + "> : reqReader :  EXCEPTION : Response Code = "
							+Integer.toString(respCode)+" ... adding chunk to missing list -->" + 
							" bytes = " + engine.start + " - " + engine.end);
					micEngine.setMissingChunk(engine.start, engine.end);
					//engine.closeConnection();
					//engine.release(true);
					engine.automaticallyReleaseConnectionToPool();
				}	            
			}			
		}
	}
	public void run() {

		startTime = System.currentTimeMillis();		
		int start, end, chunkSz, noChunks;
		long speed = -1, timeout = 0;
		reader.start();
		HttpEngine engine = null;
		while(true) {

			if(TYPE == 1)
			{
				while(!ConnectionStatus.WIFI)
				{
					setBothInfcUp(false);
					isDone = true;
				}
			}
			else
			{
				while(!ConnectionStatus.MOBILE)
				{
					setBothInfcUp(false);
					isDone = true;
				}
			}
			
			setBothInfcUp(true);
			synchronized (calcSpeed)
			{
				if(speedAvl)
				{
					speed = speedObj.getSpeed();
					if(isBothInfcUp())
					{
						timeout = speedObj.getTimeout();						
					}
					else timeout = 0;
				}				
			}
			
			if(TYPE == 1)
			{
				impl.setWifiConnTimeout((int)timeout);
				impl.setWifiReadTimeout((int)timeout);
			}
			else
			{
				impl.setMobConnTimeout((int)timeout);
				impl.setMobReadTimeout((int)timeout);
			}	

			//chnkInfo = micEngine.getChunkInfo(speed, TYPE);
			chunkList = micEngine.getChunkInfo(speed, TYPE);
			
			for(ChunkInfo chnkInfo : chunkList)
			{
				if(chnkInfo.isComplete)
				{
					synchronized(senderWait)
					{
						//System.out.println("MIC: HttpWorker <"+type+"> : senderDone!!.. starting wait..");
						//senderDone = true;
						try
						{
							System.out.println("MIC: HttpWorker <"+type+"> : senderDone!!.. starting wait..");
							senderDone = true;
							senderWait.wait();
						}
						catch (InterruptedException e)
						{
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					System.out.println("MIC: HttpWorker <"+type+"> : wait complete.. worker ending..");
					isDone = true;
					synchronized(this)
					{
						try
						{
							this.wait(1000);
						}
						catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					endTime = System.currentTimeMillis();
					if(TYPE == 1){
						stats.setWifiStartTime(startTime);
						stats.setWifiEndTime(endTime);
						stats.setWifiContentLength(responseLength);
					}
					else{
						stats.setMobileStartTime(startTime);
						stats.setMobileEndTime(endTime);
						stats.setMobileContentLength(responseLength);
					}
					
					return;

				}


				start = chnkInfo.getChunkStart();
				end = chnkInfo.getChunkEnd();
				//chunkSz = chnkInfo.getChunkSize();

				//noChunks = chnkInfo.getNoChunks();

				requestLength = requestLength + end - start + 1;

				///////////////// this is where request headers are set for workers
				RawHeaders tmpHeader = new RawHeaders();
				tmpHeader.set("Range", "bytes=" + start + "-" + end);
				/*
					if(impl.getURL().getProtocol().toLowerCase().equals("https"))
					{
						System.out.println("MIC: HttpWorker: HttpWorker.run() : HTTPS url : "+ impl.getURL().toString());
					}
				 */
				tmpHeader.set("Connection", "keep-alive");

				if(impl.isHttps())
				{
					System.out.println("MIC: HttpWorker <"+type+"> : impl isHttps");
					impl = (HttpUrlConnectionDelegate)impl;
				}
				System.out.println("MIC: HttpWorker <"+type+"> : calling newHttpEngine()");

				try
				{
					engine = impl.newHttpEngine(TYPE, method, tmpHeader, null, null);
					engine.start = start;
					engine.end = end;

					if(logEnable)
						System.out.println("MIC - Worker: <" + type + "> sending Request for " + impl.getURL().toString() 
								+ " bytes = " + start + " - " + end);
					
					//currentTimestamp = getCurrentTimeStamp();
					//startTimeBeforeSocketCreation = System.currentTimeMillis();
					
					engine.sendRequest();

				}
				catch(Exception e)
				{
					System.out.println("MIC - EXCEPTION in Sending Req from Worker: <" + type + "> ... adding chunk to missing list -->" + 
							" bytes=" + start + " - " + end);

					if(engine != null) {
						micEngine.setMissingChunk(start, end);
					}
					e.printStackTrace();
				}

				//startTimeAfterSocketCreation = System.currentTimeMillis();

				//engine.forceRelease();
				synchronized (readerQLock)
				{
					readerQueue.add(engine);													
					readerQLock.notify();
				}
			}

			
		}

	}

	protected static synchronized boolean isBothInfcUp() {
		return bothInfcUp;
	}

	protected static synchronized void setBothInfcUp(boolean bothInfcUp) {
		NewWorker.bothInfcUp = bothInfcUp;
	}

	enum Retry {
		NONE,
		SAME_CONNECTION,
		DIFFERENT_CONNECTION
	}
}
