package libcore.net.http;

import java.io.InputStream;
import libcore.io.IoUtils;
import libcore.util.Objects;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;

import libcore.io.ErrnoException;
import java.util.Map.Entry;
import java.io.FileDescriptor;
import java.io.File;
import libcore.io.IoUtils;
import libcore.io.Libcore;
import static libcore.io.OsConstants.*;

//@TODO - Collect statistics for failed and discarded chunks from here as well as from the Buffer

public class HttpWorker extends Thread {

	private HttpURLConnectionImpl impl;
	private HttpHelper helper;
	private String type;
	private int TYPE;
	//private String method = HttpEngine.POST;
	private String method = HttpEngine.GET;

	private int redirectionCount;
	private int marker;
	private int chunkSize;
	private boolean isHttps;
	private boolean isThrottle;
	private boolean logEnable;

	private final int throttleVal;

	private final int cap;

	private Average avg;

	private final int outstandingRequests;

	private final Object mLock = new Object();

	LinkedList<HttpEngine> requestQueue;

	private RequestReader reader;

	int prevChunkSize;
	long currSpeed = 0, prevSpeed = -1;

	//int newMarker = 0;

	boolean isReaderDone = false;
	boolean isSenderDone = false;
	//boolean got416 = false;
	int downloadSize = -1;

	public HttpWorker(HttpURLConnectionImpl i, HttpHelper hp, String t, boolean doOutput) {
		this.isHttps = i.isHttps;
		helper = hp;
		type = t;
		TYPE = (type.equals("wifi"))?1:2;
		impl = i;
		redirectionCount = 0;
		throttleVal = HttpHelper.Throttle;
		isThrottle = (throttleVal > 0)?true:false;
		try {
			if (doOutput) {
				System.out.println("MIC: HttpWorker() ->  doOutput = true");
				if (method == HttpEngine.GET) {
					System.out.println("MIC: HttpWorker ->  method = "+method);
					// they are requesting a stream to write to. This implies a POST method
					method = HttpEngine.POST;
					System.out.println("MIC: HttpWorker() ->  set method to "+method);
				} else if (method != HttpEngine.POST && method != HttpEngine.PUT) {
					// If the request method is neither POST nor PUT, then you're not writing
					throw new ProtocolException(method + " does not support writing");
				}
			}			
		} catch (IOException e) {
			System.out.println("MIC: HttpWorker:: Exception in Changing method: ");
			e.printStackTrace();
			//throw e;
		}
		logEnable = HttpHelper.logEnable;
		cap = helper.getBufferSize() / 2;
		avg = new Average();
		this.outstandingRequests = HttpHelper.outstandingRequests;
		requestQueue = new LinkedList<HttpEngine>();
		reader = new RequestReader();
		start();
	}

	private class Average {
		final int MAX = HttpHelper.SMOOTHING_WNDW_SZ;
		private long sum;
		int currnoelem;
		LinkedList<Long> list = new LinkedList<Long>();

		Average() {
			sum = 0;
			currnoelem = 0;
		}

		long getAverage() {
			return sum/Math.min(currnoelem, MAX);
		}

		void updateSum(long value) {
			if(currnoelem < MAX) {
				sum += value;
			} else {
				sum = sum - list.removeFirst() + value;
			}
			list.add(value);
			currnoelem++;
		}

	}

	private void done() {
		synchronized(helper.lock) {
			helper.isComplete = true;
		}
	}

	private String getCurrentTimeStamp() {
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		Date now = new Date();
		String strDate = sdfDate.format(now);
		return strDate;
	}

	private class RequestReader extends Thread {

		RequestReader() {

		}
		/*		
		private void insertLastChunk(int start, String type) throws InterruptedException {
			helper.insertLastChunkStart(start, type);
		}
		 */
		public void run() {
			System.out.println("622 - Worker: <" + type + "> ... Reader Kicked in...");
			boolean runOnce = true;
			HttpEngine engine = null;
			int start=0,end=0;
			int responseCode = 0;
			int lastChunkStart = -1;
			while(true) {
				try {
					responseCode = 0;
					synchronized(mLock) {
						while(requestQueue.size() == 0) {
							if(isSenderDone /*&& helper.missingListSize() == 0*/) {
								isReaderDone = true;
								System.out.println("622 - Worker: <" + type + "> ... Reader Exiting...");
								//if(lastChunkStart == -1) {
								//	System.out.println("622 - Worker: <" + type + "> ...lastChunk value not set !!");
								//}
								//insertLastChunk(lastChunkStart, type);
								mLock.notifyAll();
								if(engine != null)
									engine.closeConnection();
								return;
							}
							System.out.println("622 - Worker: <" + type + "> ... Reader waiting for requests");
							mLock.wait();
						}
						engine = requestQueue.removeFirst();
						//mLock.notifyAll();
					}

					engine.readResponse();
					responseCode = engine.getResponseCode();
					/* Fix for CaptivePortal tracker - 204 */
					/* Fix for HTTP_OK - 200. Some servers like wikipedia return 200 instead of 206 i.e. these servers does not serve byte-range requests */

					if(responseCode == HttpURLConnection.HTTP_NO_CONTENT /*204*/ || responseCode == HttpURLConnection.HTTP_OK/*200*/)
					{
						done();
						helper.set416();
						synchronized(mLock) {
							isReaderDone = true;
							isSenderDone = true;
						}
						helper.supportByteRequest = false;
						impl.httpEngine = engine;
						if(logEnable)
							System.out.println("622 - Worker: <" + type + ">... No Content <204> OR 200 ok ... Reader exiting");
						/* There should be just one inputstream and that's it! */
						helper.setInputStream(engine.getResponseBody());
						synchronized(HttpHelper.joinLock) {
							HttpHelper.joinLock.notify();
						}
						return;
					}


					String chunk = engine.getChunkMarkings();
					/* This will throw if response code is anything but 206 (Partial Content) */
					start = Integer.parseInt(chunk.split("-")[0]);
					end = Integer.parseInt(chunk.split("-")[1]);
					if(logEnable)
						System.out.println("622 - Worker: <" + type + ">...response code for chunk = " + start + " - " + end 													+ " == " + responseCode);

					//else/*Successful*/ {

					//redirectionCount = 0;
					//redirection = false;

					if(runOnce) {
						synchronized(HttpHelper.joinLock) {
							HttpHelper.joinLock.notify();
						}   
						runOnce = false;
					}

					if(downloadSize == -1) {
						synchronized(helper.lock) {
							if((downloadSize = helper.getDownloadSize()) == -1) {
								downloadSize = engine.getDownloadSize();
								helper.setDownloadSize(downloadSize);
							}
						}
						System.out.println("622 - Worker: <" + type + ">... DOWNLOAD SIZE = " + downloadSize);
					}
					//@TODO - Added for testing
					impl.httpEngine = engine;

					InputStream result = engine.getResponseBody();
					if(result != null) {
						if(logEnable)
							System.out.println("622 - Worker: <" + type + ">...Downloading data corresponding to KEY = " + start);
						int nRead;
						int bytesRead = 0;
						byte data[];
						ByteArrayOutputStream buffer = new ByteArrayOutputStream();
						//if(prevChunkSize != chunkSize)
						data = new byte[end-start+1];
						long startTime = System.currentTimeMillis();
						while ((nRead = result.read(data, 0, data.length)) != -1) {
							buffer.write(data, 0, nRead);
							bytesRead += nRead;
						}

						buffer.flush();
						data = buffer.toByteArray();
						if(logEnable)
							System.out.println("622 - Worker: <" + type + ">... KEY = " + start + "...downloaded bytes = " + bytesRead + "/" + (end-start+1));
						//@TODO - Fix this
						long timeTakenIncSocketCreation = System.currentTimeMillis() - startTime;
						//engine.getRequestTimeStampMillis();
						//startTimeBeforeSocketCreation;
						if(timeTakenIncSocketCreation == 0) {
							timeTakenIncSocketCreation = 1;
						}
						synchronized(mLock) {
							prevSpeed = currSpeed;
							currSpeed = (bytesRead*1000l)/timeTakenIncSocketCreation;
							if(HttpHelper.IMPL_TYPE != HttpHelper.FIX_SZ)
								avg.updateSum(currSpeed);
							System.out.println("622 - Worker: <" + type + ">...Speed = " + currSpeed);
							prevChunkSize = chunkSize;
						}


						helper.bArrInpStream.write(start, data, TYPE, bytesRead, engine.getRequestTimeStamp(), 
								timeTakenIncSocketCreation, engine.getRequestTimeStampMillis());

						if(isThrottle && timeTakenIncSocketCreation < throttleVal*1000) {
							long timeToSleep = throttleVal*1000 - timeTakenIncSocketCreation;
							System.out.println("622 - Worker: <" + type + ">...Throttled, sleeping = " + timeToSleep + " ms");
							Thread.sleep(timeToSleep);
						}

						/* Check for connection-close sent by server */
						engine.hasConnectionClosed();
					}   
					else {
						System.out.println("622 - Worker: <" + type + ">... NULL INPUTSTREAM FOR KEY = " + start);
						throw new Exception("622 - Null InputStream !!! for key = " + start);
						//helper.insertToMissingList(start, end);
					}   

					//}



				} catch (Exception e) {
					if(logEnable)
						System.out.println("622 - Worker: <" + type + "> ...Exception in Reader...code = " + responseCode);
					if(responseCode == 504) {
						if(logEnable)
							System.out.println("622 - Worker: <" + type + ">...Response Code = 504, Gateway timed out...Retry...");
						String requestChunk = engine.getRequestChunkMarkings();
						start = Integer.parseInt(requestChunk.split("-")[0]);
						end = Integer.parseInt(requestChunk.split("-")[1]);
						helper.insertToMissingList(start, end);
						long timeTakenIncSocketCreation = (engine.getRequestTimeStampMillis() != 0) ? 
								System.currentTimeMillis() - engine.getRequestTimeStampMillis() : 0;
								helper.insertStatistics(start, end-start+1, engine.getRequestTimeStamp(), 
										timeTakenIncSocketCreation, TYPE, HttpHelper.M_FAILED, 
										engine.getRequestTimeStampMillis());
					}
					else if (responseCode >= 400/*HTTP_BAD_REQUEST*/) {
						done();
						String requestChunk = engine.getRequestChunkMarkings();
						start = Integer.parseInt(requestChunk.split("-")[0]);
						System.out.println("622 - BAD Request for Worker: <" + type + ">...sending chunk start = " + start);
						lastChunkStart = start;
						helper.set416();
						//return;
					} 
					else {

						if(downloadSize != -1) {
							try {
								String requestChunk = engine.getRequestChunkMarkings();
								start = Integer.parseInt(requestChunk.split("-")[0]);
								if(start >= downloadSize) {
									if(logEnable)
										System.out.println("622 - Worker: <" + type + ">...Stashing request with start = " + start);	
								} else {
									end = Integer.parseInt(requestChunk.split("-")[1]);
									if(logEnable)
										System.out.println("622 -  Worker: <" + type + ">...Reader adding chunk to missing list-->" + 
												" bytes=" + start + " - " + end);
									long timeTakenIncSocketCreation = (engine.getRequestTimeStampMillis() != 0) ? 
											System.currentTimeMillis() - engine.getRequestTimeStampMillis() : 0;				
											helper.insertStatistics(start, end-start+1, engine.getRequestTimeStamp(), 
													timeTakenIncSocketCreation, TYPE, HttpHelper.M_FAILED,
													engine.getRequestTimeStampMillis());
											helper.insertToMissingList(start, end);

											if(engine != null)
												engine.closeConnection();
								}
							} catch (Exception ex) {
								System.out.println("622 -  Worker: <" + type + ">...Reader....Exception within exception..");
								ex.printStackTrace();
							}
							if(logEnable)
								e.printStackTrace();
						} else {
							System.out.println("622 -  Worker: <" + type + ">...Reader in Exception downloadSize not set!!!");
							e.printStackTrace();
							return;
						}

					}

				}
				try{
					synchronized(mLock) {
						mLock.notifyAll();
					}
				} catch(Exception ae) {
					System.out.println("622 - Worker: <" + type + ">...Reader exception while notifying...");
					ae.printStackTrace();
				}

			}
		}
	}

	public void run() {
		/* Start the Reader Thread */
		reader.start();
		/* We start with minimum */
		chunkSize = helper.getChunkSize();
		prevChunkSize = chunkSize;
		//long currSpeed = 0, prevSpeed = -1;
		int start=0, end=0;
		//boolean redirection = false;
		//boolean runOnce = true;
		//byte data[] = new byte[chunkSize];

		//long startTimeBeforeSocketCreation;
		//long timeTakenIncSocketCreation = 0;
		//long startTimeAfterSocketCreation;
		//String currentTimestamp = "";

		//boolean skip;
		Entry<Integer, Integer> missingChunkMarker = null;

		HttpEngine engine = null;
		while(true) {

			try {
				missingChunkMarker = helper.getNextMissingChunk();
				synchronized(mLock) {
					/* Wait if request queue is full */
					while(requestQueue.size() == outstandingRequests) {
						if(logEnable)
							System.out.println("622 - Worker: <" + type + "> ... Threshold for sending requests reached...waiting");
						mLock.wait();
					}

					/* Sender is done if we have reached EOF and there are no missing chunks left */
					if(helper.got416() && missingChunkMarker == null)
						isSenderDone = true;
					else
						isSenderDone = false;
					/* Sender waits for reader to finish off */
					while(isSenderDone && !isReaderDone) {
						//if(logEnable)
						System.out.println("622 - Worker: <" + type + 
								"> ... Sender is waiting for reader to complete");
						mLock.wait();
						missingChunkMarker = helper.getNextMissingChunk();
						if(missingChunkMarker != null) {
							isSenderDone = false;
						}
					}
				}


				//skip = false;
				//boolean byPass = false;
				synchronized(helper.lock) {

					if(helper.isComplete) {
						synchronized(mLock) {
							if(isSenderDone) {
								//if(logEnable)
								System.out.println("622 - Worker: <" + type + ">... Sender EXITING");
								mLock.notifyAll();
								return;
							}
						}
						//else
						//	byPass = true;

						/* Change worker type, when download is complete and we still have a missing chunk(s) */
						/* At this point, only one worker would be running, so we toggle and try whichever works out. */
						/*
				if(logEnable)
					System.out.println("622 - Worker: <" + type + ">... Toggling worker ...");
				if(type.equals("wifi")) {
					type = "mobile";
				}
				else if(type.equals("mobile")) {
					type = "wifi";
				}
						 */
					}   
				}  

				//if(!byPass) 
				if(type.equals("wifi")) {
					synchronized(ConnectionStatus.o_wifi) {
						if(!ConnectionStatus.WIFI)
						{
							//HttpHelper.interfaceIPAddr_wifi = null;
							//if(logEnable)
							System.out.println("622 - Worker: <" + type + ">... Waiting for WIFI to be AVAILABLE");
							/* Reset chunksize before sleeping, so that other worker can use more space */
							helper.w_setChunkSize(0);
							if(missingChunkMarker != null) {
								helper.insertToMissingList(missingChunkMarker.getKey(), missingChunkMarker.getValue());
								missingChunkMarker = null;
							}
							/* Wake the other worker in case I go to sleep */
							//		synchronized(helper.workerLock) {
							//			helper.workerLock.notifyAll();
							//		}
							ConnectionStatus.o_wifi.wait();
							synchronized(helper.lock) {
								missingChunkMarker = helper.getNextMissingChunk();
								if(helper.isComplete && missingChunkMarker == null) {
									synchronized(mLock) {
										isSenderDone = true;
										mLock.notifyAll();
									}	
									//if(logEnable)
									System.out.println("622 - Worker: <" + type + ">... Sender EXITING");
									return;
								}
							}
							/* Restore chunksize after waking up */
							helper.w_setChunkSize(chunkSize);
						}
					}
				}
				else { 
					synchronized(ConnectionStatus.o_mobile) {
						if(!ConnectionStatus.MOBILE)
						{
							//HttpHelper.interfaceIPAddr_mobile = null;
							//if(logEnable)
							System.out.println("622 - Worker: <" + type + ">... Waiting for MOBILE to be AVAILABLE");
							helper.m_setChunkSize(0);
							if(missingChunkMarker != null) {
								helper.insertToMissingList(missingChunkMarker.getKey(), missingChunkMarker.getValue());
								missingChunkMarker = null;
							}
							/* Wake the other worker in case I go to sleep */
							//		synchronized(helper.workerLock) {
							//			helper.workerLock.notifyAll();
							//		}
							ConnectionStatus.o_mobile.wait();
							synchronized(helper.lock) {
								missingChunkMarker = helper.getNextMissingChunk();
								if(helper.isComplete && missingChunkMarker == null) {
									synchronized(mLock) {
										isSenderDone = true;
										mLock.notifyAll();
									}
									//if(logEnable)
									System.out.println("622 - Worker: <" + type + ">... Sender EXITING");
									return;
								}
							}
							helper.m_setChunkSize(chunkSize);
						}
					}
				}

				/* Wake the other worker in normal case */
				//synchronized(helper.workerLock) {
				//	 helper.workerLock.notifyAll();
				//}

				//if(!redirection) {



				/*
				int otherWorkerChunkSize = (TYPE == 1) ? helper.getMobileQueueSize() : helper.getWifiQueueSize();
				int spaceLeft;
                while((spaceLeft = helper.bArrInpStream.capacity() - otherWorkerChunkSize) <= 0) {
					System.out.println("622 - Worker: <" + type + "...No space left...waiting..");
					synchronized(helper.workerLock) {
						helper.workerLock.wait();
					}*/
				/* We skip to cover the case where the other worker might have failed downloading its chunk */
				//	skip = true;
				//} 

				//if(skip) {
				//	System.out.println("622 - Worker: <" + type + "...skipping");
				//	throw new Exception("622 - Worker: <" + type + "...Skipping no space left case");
				//}

				if(missingChunkMarker == null) {
					if(HttpHelper.IMPL_TYPE == HttpHelper.VAR_SZ) {

						if(HttpHelper.VAR_IMPL_TYPE == HttpHelper.T_AIMD) {
							/* Additive Increase */
							if(currSpeed > prevSpeed) {
								chunkSize += HttpHelper.VAR_IMPL_VAL;
								if(chunkSize > cap) {
									chunkSize = cap;
									System.out.println("622 - Worker: <" + type + ">...ChunkSize set to the max CAP");
								} else {
									System.out.println("622 - Worker: <" + type + ">...Chunksize increased to = " + chunkSize );
								}   
							}
							/* Multiplicative Decrease */
							else if(currSpeed < prevSpeed) {
								chunkSize /= 2;
								if(chunkSize < HttpHelper.CHUNKSIZE_MIN)
									chunkSize = HttpHelper.CHUNKSIZE_MIN;
								System.out.println("622 - Worker: <" + type + ">...Chunksize reduced to = " + chunkSize );
							}
							/* No Change */
							else {
								System.out.println("622 - Worker: <" + type + ">...No change to chunkSize...");
							}
						} else /* Timed Variable Chunk size implementation */{
							if(currSpeed > 0) {
								synchronized(mLock) {
									/* chunksz (in bytes) = prevspeed (in bytes/sec) x timed_deadline (in seconds) */
									chunkSize = (int) (/*currSpeed*/ avg.getAverage() * ((double)HttpHelper.VAR_IMPL_VAL/1000));; 		
									if(chunkSize > cap) {
										chunkSize = cap;
										System.out.println("622 - Worker: <" + type + ">...ChunkSize set to the max CAP");
									} else {
										if(chunkSize < HttpHelper.CHUNKSIZE_MIN)
											chunkSize = HttpHelper.CHUNKSIZE_MIN;
										System.out.println("622 - Worker: <" + type + ">...TIMED case, chunksize = " + chunkSize);
									}
								}
							}
						} 

						/*	if(TYPE == 1)  
                		helper.w_setChunkSize(chunkSize); 
                	else 
                    	helper.m_setChunkSize(chunkSize);
						 */
					}

					while( chunkSize > helper.bArrInpStream.capacity() ) {
						System.out.println("622 - Worker: <" + type + "...Capacity of Buffer reached..waiting..");
						synchronized(HttpHelper.capacityLock) {
							HttpHelper.capacityLock.wait();
						}
					}

					synchronized(helper.lock) {
						marker = helper.getMarker();
						int newMarker = marker + chunkSize;
						helper.setMarker(newMarker);
						start = marker;
						end = start + chunkSize - 1;
					}

				} else {
					start = missingChunkMarker.getKey();
					end = missingChunkMarker.getValue();
					if(logEnable)
						System.out.println("622 - Worker: <" + type + "...Downloading missing chunk = " + start + "-" + end + " ...");
				}
				//}

				///////////////// this is where request headers are set for workers

				RawHeaders tmpHeader = new RawHeaders();
				tmpHeader.set("Range", "bytes=" + start + "-" + end);
				if(impl.mUrl.getProtocol().toLowerCase().equals("https"))
				{
					System.out.println("MIC: HttpWorker: HttpWorker.run() : HTTPS url : "+ impl.mUrl.toString());
				}
				tmpHeader.set("Connection", "keep-alive");

				//////////////// new HttpEngine intance is created. Source of error?

				engine = new HttpEngine(TYPE, impl, method, tmpHeader, null, null); 

				if(logEnable)
					System.out.println("622 - Worker: <" + type + ">...sending to " + impl.mUrl.toString() + 
							" ........bytes = " + start + " - " + end);

				synchronized(helper.lock) {
					if(type.equals("wifi"))
						HttpHelper.connectionFor("wifi");
					else
						HttpHelper.connectionFor("mobile");

					//currentTimestamp = getCurrentTimeStamp();
					//startTimeBeforeSocketCreation = System.currentTimeMillis();
					engine.sendRequest();
					//startTimeAfterSocketCreation = System.currentTimeMillis();
				}
				//engine.release(true);
				//engine.automaticallyReleaseConnectionToPool();
				engine.forceRelease();
				synchronized(mLock) {
					requestQueue.add(engine);
					mLock.notifyAll();
				}
			} catch(Exception e) {
				if(logEnable) {
					System.out.println("622 - Exception worker: <" + type + ">... in Writer");
					//if(missingChunkMarker != null) {
					//	helper.insertToMissingList(missingChunkMarker.getKey(), missingChunkMarker.getValue());
					//}
					System.out.println("622 - EXCEPTION Worker: <" + type + ">...adding chunk to missing list-->" + 
							" bytes=" + start + " - " + end);
				}
				if(engine != null) {
					helper.insertToMissingList(start, end);
					long timeTakenIncSocketCreation = (engine.getRequestTimeStampMillis() != 0) ? 
							System.currentTimeMillis() - engine.getRequestTimeStampMillis() : 0;
							helper.insertStatistics(start, end-start+1, engine.getRequestTimeStamp(), timeTakenIncSocketCreation, 
									TYPE, HttpHelper.M_FAILED, engine.getRequestTimeStampMillis());
				}
				e.printStackTrace();
			}
		}
	}
}
/*
			engine.readResponse();
			int responseCode = engine.getResponseCode();
			if(logEnable)
				System.out.println("622 - Worker: <" + type + ">...response code for bytes = " + start + " - " + end + 
								" == " + responseCode);

			if(responseCode == 504) {
				if(logEnable)
					System.out.println("622 - Worker: <" + type + ">...Response Code = 504, Gateway timed out...Retry...");
				helper.insertToMissingList(start, end);
			}*/
//else if (responseCode >= 400/*HTTP_BAD_REQUEST*/) {
/*	done();
				if(logEnable)
					System.out.println("622 - BAD Request for Worker: <" + type + ">...sending" + " bytes=" + start + " - " + end 											+ " ....exiting");
				helper.bArrInpStream.write(start, null, -1, 0, "", 0);
				return;
			}*/
/*
			else if(responseCode == HttpURLConnection.HTTP_MULT_CHOICE || responseCode == HttpURLConnection.HTTP_MOVED_PERM
						|| responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_SEE_OTHER) 			 
			{

				if (++redirectionCount > HttpEngine.MAX_REDIRECTS) {
					if(logEnable)
						System.out.println("622 - Worker: <" + type + ">... Too Many Redirects...exiting");
					done();
					helper.isGarbled = true;
					return;

				} 

				String location = "";
				RawHeaders rawHeaders = engine.getResponseHeaders().getHeaders();
				location = rawHeaders.get("Location");
				if(logEnable)
					System.out.println("622 - Worker: <" + type + ">...redirecting to = " + location + " ...for bytes=" + 
									start + " - " + end);
				try {
					impl.setUrl(location);
				} catch(IOException e) {
					done();
					helper.isGarbled = true;
					if(logEnable)
						System.out.println("622 - Worker: <" + type + ">...Error changing to new URL...exiting");
					e.printStackTrace();
					return;
				}
				redirection = true;
			}*/
//else/*Successful*/ {
/*
				InputStream result = engine.getResponseBody();
				redirectionCount = 0;
				redirection = false;
 */
/* Fix for CaptivePortal tracker - 204 */
/* Fix for HTTP_OK - 200. Some servers like wikipedia return 200 instead of 206 i.e. these servers does not serve byte-range requests */
//	if(responseCode == HttpURLConnection.HTTP_NO_CONTENT /*204*/ || responseCode == HttpURLConnection.HTTP_OK/*200*/)
/*	{
					done();
					helper.supportByteRequest = false;
					impl.httpEngine = engine;
					if(logEnable)
						System.out.println("622 - Worker: <" + type + ">... No Content <204> OR 200 ok ... exiting");
 */
/* There should be just one inputstream and that's it! */
/*		helper.setInputStream(result);
					synchronized(HttpHelper.joinLock) {
						HttpHelper.joinLock.notify();
					}
					return;
				}

				if(runOnce) {
                    synchronized(HttpHelper.joinLock) {
                        HttpHelper.joinLock.notify();
                    }   
                    runOnce = false;
					//@TODO - Added for testing
					impl.httpEngine = engine;
                }   

                if(result != null) {
                    if(logEnable)
					System.out.println("622 - Worker: <" + type + ">...Downloading data corresponding to KEY = " + start + 
										" timestamp = " + getCurrentTimeStamp());
					int nRead;
					int bytesRead = 0;
					ByteArrayOutputStream buffer = new ByteArrayOutputStream();
					if(prevChunkSize != chunkSize)
						data = new byte[chunkSize];
					while ((nRead = result.read(data, 0, data.length)) != -1) {
						buffer.write(data, 0, nRead);
						bytesRead += nRead;
					}
					long endTime = System.currentTimeMillis();
					buffer.flush();
					data = buffer.toByteArray();
					if(logEnable)
						System.out.println("622 - Worker: <" + type + ">... KEY = " + start + "...downloaded bytes = " + 
										bytesRead + "/" + chunkSize);
					timeTakenIncSocketCreation = endTime - startTimeBeforeSocketCreation;
					if(timeTakenIncSocketCreation == 0) {
						timeTakenIncSocketCreation = 1;
					}
					prevSpeed = currSpeed;
					currSpeed = (bytesRead*1000l)/timeTakenIncSocketCreation;
					avg.updateSum(currSpeed);
					System.out.println("622 - Worker: <" + type + ">...Speed = " + currSpeed);
					prevChunkSize = chunkSize;

					helper.bArrInpStream.write(start, data, TYPE, bytesRead, currentTimestamp, timeTakenIncSocketCreation);

					if(isThrottle && timeTakenIncSocketCreation < throttleVal*1000) {
						long timeToSleep = throttleVal*1000 - timeTakenIncSocketCreation;
						System.out.println("622 - Worker: <" + type + ">...Throttled, sleeping = " + timeToSleep + " ms");
						Thread.sleep(timeToSleep);
					}
 */		
/* Reuse the connection */
/*		engine.release(true);
					engine.automaticallyReleaseConnectionToPool();
				}   
                else {
                    System.out.println("622 - Worker: <" + type + ">... NULL INPUTSTREAM FOR KEY = " + start);
                }   

			}
		} catch(InterruptedException e) {
			System.out.println("622 - EXCEPTION Worker: <" + type + "> ...Interrupted");	
			e.printStackTrace();
		} 
		catch(Exception e) {
				if(!skip) {
				System.out.println("622 - EXCEPTION Worker: <" + type + ">...adding chunk to missing list-->" + 
								" bytes=" + start + " - " + end);
				helper.insertToMissingList(start, end);
				helper.insertStatistics(start, (end-start+1),currentTimestamp, 0, TYPE, HttpHelper.M_FAILED);
				e.printStackTrace();
				}
		}

		}

	}

}*/
