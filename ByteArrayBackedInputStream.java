package libcore.net.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.TreeMap;
import java.util.Arrays;

public class ByteArrayBackedInputStream extends InputStream {

	private volatile TreeMap<Integer, byte[]> bucket;

	private volatile int capacity;

	private int readMarker;

	private final int WIFI = 1;
	private final int MOBILE = 2;

	private int wifiCounter = 0, mobileCounter = 0;
	private final Object lock = new Object();

	private HttpHelper helper;

	private boolean available;

	public ByteArrayBackedInputStream(HttpHelper helper, int bufferSize) {
		this.helper = helper;
		bucket = new TreeMap<Integer, byte[]>();
		capacity = bufferSize;
		readMarker = 0;
		available = true;
	}

	public int wifi_bytes() {
		return wifiCounter;
	}

	public int mobile_bytes() {
		return mobileCounter;
	}

	public int getCurrentReadMarker() {
		synchronized(lock) {
			return readMarker;
		}
	}

	public int capacity() {
		synchronized(lock) {
			return this.capacity;
		}
	}

	public void write(int start, byte arr[], int TYPE, int length, String timeStamp, long timeTakenIncSocketCreation, 
			long reqTimeMillis) throws InterruptedException {
		synchronized(lock) {

			while(length > capacity) {
				if(readMarker == start) {
					System.out.println("622: ByteArrayBackedInputStream write()- SPECIAL CASE");
					capacity += length;
					break;
				} else {
					//if(HttpHelper.logEnable)
					System.out.println("622: ByteArrayBackedInputStream write()- Buffer Full...stashing chunk = [" + start + "-"
							+ (start+length-1) + "]...inserting to missing list...readMarker = " + readMarker);
					helper.insertToMissingList(start, start+length-1);
					helper.insertStatistics(start, length, timeStamp, timeTakenIncSocketCreation, TYPE, 
							HttpHelper.M_DISCARDED, reqTimeMillis);
					return;
				}
				//System.out.println("622: ByteArrayBackedInputStream write()- Buffer Full...Waiting for it to be empty");
				//lock.wait();
			}

			if(arr != null) {
				//if(HttpHelper.logEnable)
				System.out.println("622: ByteArrayBackedInputStream write()- Inserting chunk = [" + start + "-" + 
						(start+length-1) + "] to the buffer");
				if(arr.length == length)
					bucket.put(start, arr);
				else if(arr.length > length) { /* Remove unused byte space */
					bucket.put(start, Arrays.copyOf(arr,length));
				}		/* arr.length cannot be less than length */
				helper.insertStatistics(start, length, timeStamp, timeTakenIncSocketCreation, TYPE, 
						HttpHelper.M_NORMAL, reqTimeMillis);
				/* Update the Buffer capacity */
				//if(HttpHelper.logEnable)
				System.out.println("622: ByteArrayBackedInputStream write() - capacity decreased from = " + capacity +
						" to " + (capacity-length)); 
				capacity = capacity - length;	
				if(TYPE == WIFI)
					wifiCounter += length;
				else if(TYPE == MOBILE)
					mobileCounter += length;
			} 
			else
				bucket.put(start, null);

			lock.notifyAll();
		}
	}


	@Override
	public int read(byte[] bytes) throws IOException {
		return read(bytes, 0, bytes.length);
	}

	@Override
	public int read(byte[] bytes, int off, int len) throws IOException {
		Arrays.checkOffsetAndCount(bytes.length, off, len);
		synchronized(lock) {
			try{
				while(bucket.isEmpty()) {
					if(readMarker == helper.getDownloadSize()) {
						int total = wifi_bytes() + mobile_bytes();
						helper.dumpStatistics(wifi_bytes(), mobile_bytes(), total);
						System.out.println("622: ByteArrayBackedInputStream read() - Exiting.................");
						return -1;
					}
					//if(HttpHelper.logEnable)
					System.out.println("622: ByteArrayBackedInputStream read() - Buffer Empty...");
					lock.wait();
				}
				int i;
				for(i = 0; i<len;) {
					int chunkStart; 
					try {
						chunkStart = bucket.firstKey();
					} catch(Exception e) {
						lock.notifyAll();
						//if(HttpHelper.logEnable)
						System.out.println("622: ByteArrayBackedInputStream read() exception - Bytes Read = " + i);
						return i;
					}
					while(readMarker < chunkStart) {
						//if(HttpHelper.logEnable)
						System.out.println("622: ByteArrayBackedInputStream read() - Waiting for data to be available..." + 
								" first key = " + chunkStart + ", readMarker = " + readMarker);
						//if(/*bucket.get(chunkStart) == null && */readMarker == helper.getDownloadSize()) {
						/*	int total = wifi_bytes() + mobile_bytes();
						helper.dumpStatistics(wifi_bytes(), mobile_bytes(), total);
						System.out.println("622: ByteArrayBackedInputStream read() - Exiting from inside LOOP for chunkstart = "
											+ chunkStart);
						return -1;
					}*/
						lock.wait();
						chunkStart = bucket.firstKey();
					}
					byte value[] = bucket.get(chunkStart);
					//if(/*value == null*/ readMarker == helper.getDownloadSize() ) {
					/*	int total = wifi_bytes() + mobile_bytes();
					helper.dumpStatistics(wifi_bytes(), mobile_bytes(), total);
					System.out.println("622: ByteArrayBackedInputStream read() - Exiting for chunkstart = " + chunkStart);
					return -1;
				}*/
					int temp_len = Math.min((readMarker > chunkStart)?(value.length-(readMarker-chunkStart)):value.length, len-i);
					System.arraycopy(value, (readMarker > chunkStart)?(readMarker-chunkStart):0, bytes, off+i, temp_len);
					readMarker = readMarker + temp_len;
					if(readMarker == (chunkStart + value.length)) {
						//if(HttpHelper.logEnable)
						System.out.println("622: ByteArrayBackedInputStream read() - capacity increased from = " + capacity +
								" to " + (capacity+value.length));
						capacity = capacity + value.length;
						bucket.remove(chunkStart);
						//synchronized(helper.workerLock) {
						//	helper.workerLock.notifyAll();
						//}

						synchronized(HttpHelper.capacityLock) {
							HttpHelper.capacityLock.notifyAll();
						}
					}
					i += temp_len;
				}
				lock.notifyAll();
				//if(HttpHelper.logEnable)
				System.out.println("622: ByteArrayBackedInputStream read() - Bytes Read = " + i);
				return i;
			} catch(Exception ie) {
				System.out.println("622 - ByteArrayBackedInputStream: - Exception in read()...");
				ie.printStackTrace();
				throw new IOException("622 - ByteArrayBackedInputStream: - Exception in read()");
				//return -1;
			}
		}

	}

	@Override
	public int read() throws IOException {
		synchronized(lock) {
			try{
				while(bucket.isEmpty()) {
					System.out.println("622: ByteArrayBackedInputStream read() - Buffer Empty...");
					lock.wait();
				}
				int chunkStart = bucket.firstKey();
				while(readMarker < chunkStart) {
					System.out.println("622: ByteArrayBackedInputStream read() - Waiting for data to be available..." + 
							" first key = " + chunkStart + ", readMarker = " + readMarker);
					lock.wait();
					chunkStart = bucket.firstKey();
				}
				byte value[] = bucket.get(chunkStart);
				if(value == null) {
					int total = wifi_bytes() + mobile_bytes();
					helper.dumpStatistics(wifi_bytes(), mobile_bytes(), total);
					return -1;
				}
				int result = value[readMarker] & 0xFF;
				readMarker++;
				if( readMarker == (chunkStart + value.length) ) {
					/* Increase capacity */
					capacity = capacity + value.length;
					/* Remove the Chunk that has been read */
					bucket.remove(chunkStart);
					//synchronized(HttpHelper.capacityLock) {
					//	HttpHelper.capacityLock.notifyAll();
					//}
					lock.notifyAll();
				}
				return result;
			} catch(Exception e) {
				System.out.println("622 - ByteArrayBackedInputStream: - Exception in read()...");
				e.printStackTrace();
				return -1;
			}
		}

	}
}

