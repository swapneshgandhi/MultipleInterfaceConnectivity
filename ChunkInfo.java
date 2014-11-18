package libcore.net.http;

public class ChunkInfo {
	
	int chunkSize, startOffset, noChunks;
	boolean isComplete;
	
	public boolean isComplete() {
		return isComplete;
	}

	public void setComplete(boolean isComplete) {
		this.isComplete = isComplete;
	}

	public int getChunkSize() {
		return chunkSize;
	}

	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

	public int getStartOffset() {
		return startOffset;
	}

	public void setStartOffset(int startOffset) {
		this.startOffset = startOffset;
	}

	public int getNoChunks() {
		return noChunks;
	}

	public void setNoChunks(int noChunks) {
		this.noChunks = noChunks;
	}

	public ChunkInfo(int chnkSz, int strtOffset, int noChnks)
	{
		chunkSize = chnkSz;
		startOffset = strtOffset;
		noChunks = noChnks;
		isComplete = false;
	}
}

