package libcore.net.http;

import java.net.Socket;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class ConnectionStatus extends Thread {
	
	private boolean useBoth;
	public static boolean WIFI;
	public static boolean MOBILE;
		
	public static Object o_wifi;
	public static Object o_mobile;
	
	private HttpHelper helper;
	private int sleepInSeconds;

	/*
	private boolean getWifiIP = true;
	private boolean getMobileIP = true;
	
	public void doNotNeedIPFor(int type) {
		synchronized(lock) {
			if(type == 1)
				getWifiIP = false;
			else
				getMobileIP = false;
		}
	}*/
	
	public static String getIp(int type) throws InterruptedException {
		System.out.println("622 - Inside Connectionstatus getIP() function");
		while(true) {
		synchronized(lock) {
			if(type == 1 && ConnectionStatus.wifiIP != null) {
				//getWifiIP = true;
				return ConnectionStatus.wifiIP;
			}
			else if(type == 2 && ConnectionStatus.mobileIP != null) {
				//getMobileIP = true;
				return ConnectionStatus.mobileIP;
			} else {
				System.out.println("622 - Connectionstatus getIP() - Waiting for IP to be available");
				lock.wait();
			}
		}
		
		}
		//return null;
	}

	private static Object lock = new Object();
	private static volatile String wifiIP = null;
	private static volatile String mobileIP = null;


	public ConnectionStatus(HttpHelper h, int sleep) {
		System.out.println("MIC: ConnectionStatus :: ConnectionStatus(HttpHelper h, int sleep = "+Integer.toString(sleep)+") -> Started");
		helper = h;
		useBoth = false;
		ConnectionStatus.WIFI = false;
		ConnectionStatus.MOBILE = false;
		o_wifi = new Object();
		o_mobile = new Object();
		sleepInSeconds = sleep;
		if(helper.runConnStatusThread) {
			start();
		} else {
			ConnectionStatus.MOBILE = true;
			ConnectionStatus.WIFI = true;
		}
		System.out.println("MIC: HttpHelper :: HttpHelper() -> Ended");
	}

	public void run() {
		if(HttpHelper.logEnable)
			System.out.println("622 - ConnectionStatus Thread now running...");
		
		DataInputStream in = null;
		DataOutputStream out = null;
		BufferedReader inFromServer = null;
		try {
			Socket socket = new Socket("127.0.0.1", 1234);
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
			inFromServer = new BufferedReader(new InputStreamReader(in));
		} catch(Exception e) {
			System.out.println("622 - ConnectionStatus Thread - Unable to open socket to server");
			e.printStackTrace();
			return;
		}
		String toSend;
		while(true) {
			
			synchronized(helper.lock) {
				if(helper.isComplete) {
					if(HttpHelper.logEnable)
						System.out.println("622 - ConnectionStatus Thread - exiting...");
					notifyWorkers(true);
					return;
				}
			}
			
			try{
				
				toSend = "wifi";
				out.writeBytes(toSend + '\n');
				String isWifiUp = inFromServer.readLine();
				//if(HttpHelper.logEnable)
					//System.out.println("622 - ConnectionStatus Thread: Wifi = " + isWifiUp);
				synchronized(ConnectionStatus.o_wifi) {
					if(isWifiUp != null && isWifiUp.equals("true"))
						ConnectionStatus.WIFI = true;
					else {
						ConnectionStatus.WIFI = false;
						ConnectionStatus.wifiIP = null;
					}
				}
				toSend = "mobile";
				out.writeBytes(toSend + '\n');
				String isMobileUp = inFromServer.readLine();
				//if(HttpHelper.logEnable)
					//System.out.println("622 - ConnectionStatus Thread: Mobile = " + isMobileUp);
				synchronized(ConnectionStatus.o_mobile) {
					if(isMobileUp != null && isMobileUp.equals("true"))
						ConnectionStatus.MOBILE = true;
					else {
						ConnectionStatus.MOBILE = false;
						ConnectionStatus.mobileIP = null;
					}
				}

                synchronized(lock) {
                    if(ConnectionStatus.WIFI) {
						toSend = "wifiIP";
                    	out.writeBytes(toSend + '\n');
                    	ConnectionStatus.wifiIP = inFromServer.readLine();
                    	//System.out.println("622 - ConnectionStatus Thread: WIFI Ip = " + wifiIP);
					} 
					if(ConnectionStatus.MOBILE) {
						toSend = "mobileIP";
                    	out.writeBytes(toSend + '\n');
                    	ConnectionStatus.mobileIP = inFromServer.readLine();
						//System.out.println("622 - ConnectionStatus Thread: Mobile Ip = " + mobileIP);
                	}
					lock.notifyAll();
				}   

			
			}catch(Exception e) {
				System.out.println("622 - ConnectionStatus Thread: Exception sending data");
				e.printStackTrace();
			}
			
			
			try {
				notifyWorkers(false);	
				Thread.sleep(sleepInSeconds*1000);
			} catch(InterruptedException e) {
				System.out.println("622 - ConnectionStatus: Interrupted Exception");
				e.printStackTrace();
			}
		}
	}

	private void notifyWorkers(boolean isComplete) {
		synchronized(ConnectionStatus.o_wifi) {
        	if(ConnectionStatus.WIFI || isComplete)
            {   
                	ConnectionStatus.o_wifi.notifyAll();
            }
		}
        synchronized(ConnectionStatus.o_mobile) {    
			if(ConnectionStatus.MOBILE || isComplete)
            {   
                	ConnectionStatus.o_mobile.notifyAll();
            }   
        }   
	}

	/*
	private void update(boolean val) {
		useBoth = val;
	}
	
	private boolean canUseBoth() {
		return useBoth;
	}
	*/
}
