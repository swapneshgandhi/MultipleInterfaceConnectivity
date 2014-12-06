package libcore.net.http;

import java.util.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class NewStatistics{
	PriorityQueue<ChunkInfo> allChunks;
	int wifiChunks;
	int mobileChunks;
	long mobileStartTime;
	long mobileEndTime;
	long wifiStartTime;
	long wifiEndTime;
	int wifiContentLength;
	int mobileContentLength;

	public void setMobileStartTime(long time){
		this.mobileStartTime = time;
	}

	public void setMobileEndTime(long time){
		this.mobileEndTime = time;
	}

	public void setWifiStartTime(long time){
		this.wifiStartTime = time;
	}

	public void setWifiEndTime(long time){
		this.wifiEndTime = time;
	}

	public void setWifiContentLength(int length){
		this.wifiContentLength = length;
		
	}

	public void setMobileContentLength(int length){
		this.mobileContentLength = length;
		
	}
	public NewStatistics(){
		this.allChunks = new PriorityQueue<ChunkInfo>(10, new Comparator<ChunkInfo>(){
			public int compare(ChunkInfo chunk1, ChunkInfo chunk2){
				if(chunk1.chunkStart > chunk2.chunkStart){
					return 1;
				}
				else{
					return -1;
				}
			}
		});
		wifiChunks = 0;
		mobileChunks = 0;
		
	}

	private class ChunkInfo{
		String timeRecieved;
		Integer chunkStart;
		Integer chunkEnd;
		Integer type;
		public ChunkInfo(String timeRecieved, Integer chunkStart, Integer chunkEnd, Integer type){
			this.timeRecieved = timeRecieved;
			this.chunkStart = chunkStart;
			this.chunkEnd = chunkEnd;
			this.type = type;
		}
	}

	public synchronized void addChunktoStats(String timeRecieved, Integer chunkStart, Integer chunkEnd, int type){
		if(type == 1){
			wifiChunks++;
		}
		else{
			mobileChunks++;
		}

		ChunkInfo chunk = new ChunkInfo(timeRecieved, chunkStart, chunkEnd, type);
		allChunks.add(chunk);
	}

	public void dumpStats(){
		File file = new File("/data/local/stats.txt");
		if(!file.exists()){
			try {
				file.createNewFile();
			} catch (IOException e1) {
				System.out.println("622 - Unable to create file!!!");
				e1.printStackTrace();
			}
		}

		PrintWriter writer = null;
		
		try
		{
		//writer = new PrintWriter(file, "UTF-8");
		writer = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
		String wifiChunksContent = "No of Wifi Chunks: " + String.valueOf(wifiChunks);
		String mobileChunksContent = "No of Mobile Chunks: " + String.valueOf(mobileChunks);	
		String totalMobileContentLength = "Mobile Content Length:" + String.valueOf(mobileContentLength);
		String totalWifiContentLength = "Wifi Content Length: " + String.valueOf(wifiContentLength);	
		String totalMobileTime = "Mobile Time(ms): " + String.valueOf(mobileEndTime - mobileStartTime);	
		String totalWifiTime = "Wifi Time(ms): " + String.valueOf(wifiEndTime - wifiStartTime);	


		writer.println(totalMobileContentLength);
		writer.println(totalWifiContentLength);
		
		writer.println(totalMobileTime);
		writer.println(totalWifiTime);

		writer.println(wifiChunksContent);
		writer.println(mobileChunksContent);

		String header = String.format("ChunkStart\t\tResponse_TimeStamp\tType");
		writer.println(header);

			while(!allChunks.isEmpty()) {
				ChunkInfo chunk = allChunks.poll();
				String content = String.format("%d\t\t%s\t\t%s", chunk.chunkStart, chunk.timeRecieved, chunk.type==1?"wifi":"mobile");
				writer.println(content);
			}
		}
		catch(FileNotFoundException ex) {
			System.out.println("file not forund exception");
			ex.printStackTrace();
		}
		
	    catch(UnsupportedEncodingException e) {
	        System.out.println("622 - Unsupported encoding!!!");
	        e.printStackTrace();
		}
		catch( Exception e) {
			System.out.println("Exception in writing to file : "+e.getMessage());
			e.printStackTrace();
		}
		finally{
			writer.close();
		}

	}


}
