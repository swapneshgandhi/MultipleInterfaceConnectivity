package libcore.net.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.Arrays;


public class NewByteArrayBackedInputStream extends InputStream {

	// this is the buffer to store the data recieved
	private volatile TreeMap<Integer, byte[]> bucket;

	private volatile int capacity;

	// read marker for the current read chunk. I guess
	private int readMarker;

	// this stores the chunks missing. The ones that we wernt able to get earlier. Due to some problem like connection lost.
	private TreeMap<Integer, Integer> missingChunks;

	// WIFI identifier
	private final int WIFI = 1;

	// DATA conncetion identifier
	private final int MOBILE = 2;


	private int wifiCounter = 0, mobileCounter = 0;

	// lock for synchronization 
	private final Object lock = new Object();

	// download file size
	private int totalDownloadSize;

	// download startOffset . This might not be 0 everytime
	private int startOffset;

	// current download pointer
	private int downloadDataPointer;

	private boolean available;

	private int FIXED_CHUNK_SIZE = 1024;

	// initialize the missing chunks tree map and bucket 
	public NewByteArrayBackedInputStream(int bufferSize, int totalDownloadSize, int offset) {
		this.totalDownloadSize = totalDownloadSize;
		this.startOffset = offset;
		this.downloadDataPointer = offset;

		missingChunks = new TreeMap<Integer, Integer>();
		bucket = new TreeMap<Integer, byte[]>();
		capacity = bufferSize;
		readMarker = 0;
		available = true;
		totalDownloadSize = 0;
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

	public void write(int start, byte arr[], int TYPE, int length) throws InterruptedException {
		synchronized(lock) {

			while(length > capacity) {
				if(readMarker == start) {
					System.out.println("MIC: ByteArrayBackedInputStream write()- SPECIAL CASE");
					capacity += length;
					break;
				} else {
					System.out.println("MIC: ByteArrayBackedInputStream write()- Buffer Full...stashing chunk = [" + start + "-"
							+ (start+length-1) + "]...inserting to missing list...readMarker = " + readMarker);
					return;
				}
			}

			lock.wait();

			if(arr != null) {

				System.out.println("MIC: ByteArrayBackedInputStream write()- Inserting chunk = [" + start + "-" + 
						(start+length-1) + "] to the buffer");
				if(arr.length == length){
					bucket.put(start, arr);
				}

				else if(arr.length > length) { /* Remove unused byte space */
					bucket.put(start, Arrays.copyOf(arr,length));
				}

				System.out.println("MIC: ByteArrayBackedInputStream write() - capacity decreased from = " + capacity +
						" to " + (capacity-length)); 
				capacity = capacity - length;	

				if(TYPE == WIFI){
					wifiCounter += length;
				}
				else if(TYPE == MOBILE)
					mobileCounter += length;
			} 
			else{
				bucket.put(start, null);
			}
		}

		lock.notifyAll();
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
					if(readMarker == totalDownloadSize) {
						System.out.println("MIC: ByteArrayBackedInputStream read() - Exiting.................");
						return -1;
					}
					lock.wait();
				}

				int i;
				for(i = 0; i<len;) {
					int chunkStart; 

					try {
						chunkStart = bucket.firstKey();
					} catch(Exception e) {
						lock.notifyAll();
						return i;
					}

					while(readMarker < chunkStart) {
						System.out.println("MIC: ByteArrayBackedInputStream read() - Waiting for data to be available..." + 
								" first key = " + chunkStart + ", readMarker = " + readMarker);
						lock.wait();
						chunkStart = bucket.firstKey();
					}

					byte value[] = bucket.get(chunkStart);

					int temp_len = Math.min((readMarker > chunkStart)?(value.length-(readMarker-chunkStart)):value.length, len-i);

					System.arraycopy(value, (readMarker > chunkStart)?(readMarker-chunkStart):0, bytes, off+i, temp_len);

					readMarker = readMarker + temp_len;

					if(readMarker == (chunkStart + value.length)) {

						System.out.println("MIC: ByteArrayBackedInputStream read() - capacity increased from = " + capacity +
								" to " + (capacity+value.length));

						capacity = capacity + value.length;

						bucket.remove(chunkStart);

					}

					i += temp_len;
				}

				lock.notifyAll();

				System.out.println("MIC: ByteArrayBackedInputStream read() - Bytes Read = " + i);
				return i;
			} catch(Exception ie) {
				System.out.println("MIC - ByteArrayBackedInputStream: - Exception in read()...");
				ie.printStackTrace();
				throw new IOException("MIC - ByteArrayBackedInputStream: - Exception in read()");
			}
		}

	}

	@Override
	public int read() throws IOException {
		synchronized(lock) {
			try{
				while(bucket.isEmpty()) {
					System.out.println("MIC: ByteArrayBackedInputStream read() - Buffer Empty...");
					lock.wait();
				}
				int chunkStart = bucket.firstKey();
				while(readMarker < chunkStart) {
					System.out.println("MIC: ByteArrayBackedInputStream read() - Waiting for data to be available..." + 
							" first key = " + chunkStart + ", readMarker = " + readMarker);
					lock.wait();
					chunkStart = bucket.firstKey();
				}
				byte value[] = bucket.get(chunkStart);
				if(value == null) {
					int total = wifi_bytes() + mobile_bytes();
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
				System.out.println("MIC - ByteArrayBackedInputStream: - Exception in read()...");
				e.printStackTrace();
				return -1;
			}
		}

	}

	public synchronized int missingListSize() {
		return missingChunks.size();
	}

	public synchronized void insertToMissingList(int start, int end) {
		missingChunks.put(start, end);
	}

	public synchronized Entry<Integer, Integer> getNextMissingChunk() {
		return missingChunks.pollFirstEntry();
	}

	public ChunkInfo getChunksForWorker(int speed, int type){
		synchronized(this){

			if(!missingChunks.isEmpty()) {
				Entry<Integer, Integer> entry = getNextMissingChunk();
				return new ChunkInfo(FIXED_CHUNK_SIZE,entry.getKey(),1);
			}

			if(downloadDataPointer >=  totalDownloadSize){
				ChunkInfo chnkInfo = new ChunkInfo(-1, -1, -1);
				chnkInfo.setComplete(true);
				System.out.println("MIC: NewByteArray: getChunksForWorker(): returning chunkInfo with Complete = true");
				return chnkInfo;
			}	

			int bytesToDownload = totalDownloadSize - downloadDataPointer;
			if(bytesToDownload < FIXED_CHUNK_SIZE && bytesToDownload > 0) {
				System.out.println("MIC: NewByteArray: getChunksForWorker(): chunk size < 1024 : "+Integer.toString(bytesToDownload));
				ChunkInfo chnkInfo = new ChunkInfo(bytesToDownload, downloadDataPointer, 1);
				downloadDataPointer = downloadDataPointer + bytesToDownload;
				System.out.println("MIC: NewByteArray: getChunksForWorker(): Sending chunkInfo: Start = "
				+Integer.toString(downloadDataPointer)+", Chunk Size = "+Integer.toString(bytesToDownload)+", No of chunks = 1");
				return chnkInfo;
			}		
			
			for(int i = 5;i >= 1;i--){
				if(totalDownloadSize - downloadDataPointer > FIXED_CHUNK_SIZE * i){
					System.out.println("MIC: NewByteArray: getChunksForWorker(): returning chunkInfo obj: Chunks Sent" + String.valueOf(i));
					ChunkInfo chnkInfo = new ChunkInfo(FIXED_CHUNK_SIZE, downloadDataPointer, i);
					downloadDataPointer = downloadDataPointer + FIXED_CHUNK_SIZE * i;
					System.out.println("MIC: NewByteArray: getChunksForWorker(): Sending chunkInfo: Start = "+Integer.toString(downloadDataPointer)
					+", Chunk Size = "+Integer.toString(bytesToDownload)+", No of chunks = "+Integer.toString(i));
					return chnkInfo;
				}
			}
			
			System.out.println("MIC: NewByteArray: getChunksForWorker(): returning NULL chunkInfo");
			ChunkInfo chnkInfo = new ChunkInfo(0, 0, 0);
			chnkInfo.setComplete(true);
			return chnkInfo;

		}	
	}
}

