package libcore.net.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class MicHttpEngine {

	HttpEngine headResponse;
	HttpURLConnectionImpl httpImpl;
	NewByteArrayBackedInputStream inputStream;
	boolean isComplete;
	ConnectionStatus connStatus;
	byte data[];
	NewWorker w1, w2;
	Object returnLock = new Object();
	private NewHelper helper;
	NewStatistics stats;
	
	public MicHttpEngine(HttpURLConnectionImpl impl) {	
		
		httpImpl = impl;
		headResponse = httpImpl.headResponse;
		inputStream = new NewByteArrayBackedInputStream(headResponse.contentLength(), headResponse.contentLength(), 0);
		isComplete = false;
		connStatus = new ConnectionStatus(this, 0);
		stats = new NewStatistics();
		
	}
	
	// to be called from workers for getting info of chunks to be downloaded
	public synchronized ArrayList<ChunkInfo> getChunkInfo(long speed, int workerType) {
		
		return inputStream.getChunksForWorker(speed, workerType);
	}
	
	
	public synchronized void setChunk(int strtOffset, int size, InputStream stream, int type) {
		
		data = new byte[size];
		try
		{
			System.err.println("MIC: MicHttpEngine: setChunk(): calling stream.read()");
			int result = stream.read(data, 0, data.length);
			System.err.println("MIC: MicHttpEngine: setChunk(): stream.read() successful: Result = " + String.valueOf(result));
		}
		catch (IOException e) {
			
			System.err.println("MIC: MicHttpEngine: setChunk(): Exception in stream.read(): "+e.getMessage());
			e.printStackTrace();
		}
		
		System.err.println("MIC: MicHttpEngine: setChunk(): skipping inputStream.write()");
		
		
		try
		{
			System.err.println("MIC: MicHttpEngine: setChunk(): calling inputStream.write()");
			inputStream.write(strtOffset, data, type, size);
			System.err.println("MIC: MicHttpEngine: setChunk(): inputStream.write() successful");
		}
		catch (InterruptedException e)
		{			
			System.err.println("MIC: MicHttpEngine: setChunk(): Exception in inputStream.write(): "+e.getMessage());
			e.printStackTrace();
		}
		
	}
	
	public synchronized void setMissingChunk(int strtOffset,  int end) {
		
		System.err.println("MIC: MicHttpEngine: setMissingChunk(): calling insertToMissingList for chunk: "+strtOffset+" - "+end);
		inputStream.insertToMissingList(strtOffset, end);
		System.err.println("MIC: MicHttpEngine: setMissingChunk(): done for chunk: "+strtOffset+" - "+end);
	}
	
	public void startWorkers(MicHttpURLConnectionImpl micHttpImpl) {
		
		synchronized (returnLock)
		{
			w1 = new NewWorker(httpImpl, connStatus, this, "wifi",stats);
			w2 = new NewWorker(httpImpl, connStatus, this, "mobile",stats);
			helper = new NewHelper(w1, w2, micHttpImpl.resultLock,stats);
		}
	}
	
	public NewByteArrayBackedInputStream getStream()
	{
		return inputStream;
	}
}

