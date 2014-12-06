package libcore.net.http;

public class MicHttpURLConnectionImpl {

	private HttpURLConnectionImpl implObj;
	private MicHttpEngine micEngine;
	int downloadSize;
	Object resultLock = new Object();
	private NewByteArrayBackedInputStream resultStream;
	private HttpEngine result;
	
	public MicHttpURLConnectionImpl(HttpURLConnectionImpl impl) {
		
		implObj = impl;
		//implObj.setConnectTimeout(500);
		//implObj.setReadTimeout(500);
		//downloadSize = implObj.headResponse.getDownloadSize();
		micEngine = new MicHttpEngine(implObj);
	}
	
	
	
	public HttpEngine getResponse()
	{		
		micEngine.startWorkers(this);
		synchronized (resultLock) {
		
			try {
				resultLock.wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		resultStream = micEngine.getStream();
		result = implObj.headResponse;
		result.setResponseBody(resultStream);
		return result;
		
	}

}
