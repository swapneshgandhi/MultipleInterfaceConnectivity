package libcore.net.http;

public class ChunkInfo {
	
	int chunkStart, chunkEnd;
	boolean isComplete;

	public void setIsComplete(boolean status){
		this.isComplete = status;
	}

	public void setChunkStart(int chunkStart){
		this.chunkStart = chunkStart;
	}

	public void setChunkEnd(int chunkEnd){
		this.chunkEnd = chunkEnd;
	}

	public boolean getIsComplete(){
		return this.isComplete;
	}

	public int getChunkStart(){
		return this.chunkStart;	
	}

	public int getChunkEnd(){
		return this.chunkEnd;
	}
	
	public ChunkInfo(int chunkStart, int chunkEnd)
	{
		this.chunkStart = chunkStart;
		this.chunkEnd = chunkEnd;
		isComplete = false;
	}
}
