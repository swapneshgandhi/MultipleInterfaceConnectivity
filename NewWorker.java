package libcore.net.http;

import java.io.InputStream;
import libcore.io.IoUtils;
import libcore.util.Objects;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
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

	private final Object engineQLock = new Object();
	private final Object senderWait = new Object();

	LinkedList<HttpEngine> readerQueue, retryQueue;

	boolean isReaderDone = false;
	boolean isSenderDone = false;

	private HttpUrlConnectionDelegate httpSimpl;
	ConnectionStatus connStatus;
	MicHttpEngine micEngine;
	ChunkInfo chnkInfo;
	boolean readerQIsEmpty, retryQIsEmpty, senderDone;

	private RequestReader reader;

	public NewWorker(HttpURLConnectionImpl i, ConnectionStatus connStatus, MicHttpEngine micEng, String intType) {

		this.connStatus = connStatus;
		micEngine = micEng;
		type = intType;
		TYPE = (type.equals("wifi"))?1:2;           //1 for Wifi, 2 for Mobile
		impl = i;
		method = impl.getRequestMethod();
		if(!method.equals(HttpEngine.GET))
		{
			System.out.println("Request method = "+impl.getRequestMethod()+"!!! Changing method to GET");
			method = HttpEngine.GET;
		}
		logEnable = HttpHelper.logEnable;
		readerQueue = new LinkedList<HttpEngine>();
		retryQueue = new LinkedList<HttpEngine>();
		senderDone = false;
		reader = new RequestReader();
		start();
	}

	private String getCurrentTimeStamp() {
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Date now = new Date();
		String strDate = sdfDate.format(now);
		return strDate;
	}	


	public class RequestReader extends Thread {


		HttpEngine engine;
		int start, end, respCode;
		IOException httpEngineFailure;
		RequestReader() {

		}

		public void run()
		{

			while(true)
			{
				while(readerQueue.isEmpty())
				{
					System.out.println("MIC: HttpWorker: <" + type + "> reqReader : readerQueue isEmpty ...");
					readerQIsEmpty = true;
					if(senderDone)
					{
						System.out.println("MIC: HttpWorker: <" + type + "> reqReader : senderDone = true ... will notify sender now ...");
						senderWait.notify();
						return;
					}
				}
				readerQIsEmpty = false;
				engine = readerQueue.removeFirst();
				
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
					micEngine.setMissingChunk(engine.start, engine.end);
					continue;	
				}				
				System.out.println("MIC: HttpWorker: <" + type + "> : reqReader : Printing Response Header:");
				engine.printResponseHeaders();
				respCode = engine.getResponseCode();
				switch(respCode)
				{
				case(206):
					System.out.println("MIC: NewWorker: <" + type + "> : reqReader : Data downloaded for Chunk: "
							+ Integer.toString(engine.start)+" - "+ Integer.toString(engine.end));
					
					micEngine.setChunk(engine.start, engine.end, engine.getResponseBody(), TYPE);
					engine.automaticallyReleaseConnectionToPool();
					break;
					
				default:					
					System.out.println("MIC: Worker: <" + type + "> : reqReader :  EXCEPTION : Response Code = "
							+Integer.toString(respCode)+" ... adding chunk to missing list -->" + 
							" bytes = " + engine.start + " - " + engine.end);
					micEngine.setMissingChunk(engine.start, engine.end);
					//engine.closeConnection();
					engine.release(true);
				}	            
			}
		}
	}
	public void run() {

		
		int start, end, chunkSz, noChunks;
		reader.start();

		HttpEngine engine = null;
		while(true) {



			if(TYPE == 1)
			{
				while(!ConnectionStatus.WIFI) {}
			}
			else
			{
				while(!ConnectionStatus.MOBILE) {}
			}

			chnkInfo = micEngine.getChunkInfo(-1, TYPE);
			if(chnkInfo.isComplete)
			{
				System.out.println("MIC: HttpWorker <"+type+"> : senderDone!!.. starting wait..");
				senderDone = true;
				try
				{
					senderWait.wait();
				}
				catch (InterruptedException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("MIC: HttpWorker <"+type+"> : wait complete.. worker ending..");
				return;
				
			}
			start = chnkInfo.getStartOffset();
			chunkSz = chnkInfo.getChunkSize();
			end = start + chunkSz - 1;
			noChunks = chnkInfo.getNoChunks();


			for(int i=1; i <= noChunks; i++)
			{
				try
				{
					///////////////// this is where request headers are set for workers
					if(i>1)
					{
						start = end + 1;
						end = start + chunkSz - 1;
					}
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
					engine = impl.newHttpEngine(TYPE, method, tmpHeader, null, null);
					engine.start = start;
					engine.end = end;

					if(logEnable)
						System.out.println("MIC - Worker: <" + type + "> sending Request for " + impl.getURL().toString() 
								+ " bytes = " + start + " - " + end);

					//currentTimestamp = getCurrentTimeStamp();
					//startTimeBeforeSocketCreation = System.currentTimeMillis();
					
					engine.sendRequest();
					//startTimeAfterSocketCreation = System.currentTimeMillis();

					engine.forceRelease();
					readerQueue.add(engine);
					/*
					synchronized(engineQLock) {
						readerQueue.add(engine);
						engineQLock.notifyAll();
					}
					*/
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
			}
			
			
		}
	}

	enum Retry {
		NONE,
		SAME_CONNECTION,
		DIFFERENT_CONNECTION
	}
}

