package libcore.net.http;

public class NewHelper extends Thread{
	
	boolean isDone;
	NewWorker w1, w2;
	Object micHttpLock;
	NewStatistics stats;

	NewHelper(NewWorker w1, NewWorker w2, Object micLock,NewStatistics stats)
	{
		isDone = false;
		this.w1 = w1;
		this.w2 = w2;
		micHttpLock = micLock;
		this.stats = stats;
		start();
	}
	
	public void run()
	{
		while(!w1.isDone || !w2.isDone)
		{}
		synchronized (micHttpLock) {	
			stats.dumpStats();	
			micHttpLock.notify();
		}
		return;
		
	}
}
