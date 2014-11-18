package libcore.net.http;

import java.io.IOException;
import java.io.InputStream;

public class MicHttpEngine {

	HttpEngine headResponse;
	HttpURLConnectionImpl httpImpl;
	NewByteArrayBackedInputStream inputStream;
	boolean isComplete;
	ConnectionStatus connStatus;
	byte data[];
	NewWorker W1, W2;
	
	public MicHttpEngine(HttpURLConnectionImpl impl) {	
		
		httpImpl = impl;
		headResponse = httpImpl.headResponse;
		inputStream = new NewByteArrayBackedInputStream(headResponse.contentLength(), headResponse.contentLength(), 0);
		isComplete = false;
		connStatus = new ConnectionStatus(this, 0);	
		
	}
	
	// to be called from workers for getting info of chunks to be downloaded
	public synchronized ChunkInfo getChunkInfo(int speed, int workerType) {
		
		return inputStream.getChunksForWorker(speed, workerType);
	}
	
	
	public synchronized void setChunk(int strtOffset, int size, InputStream stream, int type) {
		
		data = new byte[size];
		try
		{
			System.err.println("MIC: MicHttpEngine: setChunk(): calling stream.read()");
			stream.read(data, 0, data.length);
			System.err.println("MIC: MicHttpEngine: setChunk(): stream.read() successful");
		}
		catch (IOException e) {
			
			System.err.println("MIC: MicHttpEngine: setChunk(): Exception in stream.read(): "+e.getMessage());
			e.printStackTrace();
		}
		try
		{
			System.err.println("MIC: MicHttpEngine: setChunk(): calling inputStream.write()");
			inputStream.write(strtOffset, data, type, size);
			System.err.println("MIC: MicHttpEngine: setChunk(): inputStream.write() successful");
		} catch (InterruptedException e) {
			
			System.err.println("MIC: MicHttpEngine: setChunk(): Exception in inputStream.write(): "+e.getMessage());
			e.printStackTrace();
		}
	}
	
	public synchronized void setMissingChunk(int strtOffset,  int end) {
		
		System.err.println("MIC: MicHttpEngine: setMissingChunk(): calling insertToMissingList for chunk: "+strtOffset+" - "+end);
		inputStream.insertToMissingList(strtOffset, end);
		System.err.println("MIC: MicHttpEngine: setMissingChunk(): done for chunk: "+strtOffset+" - "+end);
	}
	
	public void startWorkers() {
		
		W1 = new NewWorker(httpImpl, connStatus, this, "wifi");
		W2 = new NewWorker(httpImpl, connStatus, this, "mobile");		
	}
}

