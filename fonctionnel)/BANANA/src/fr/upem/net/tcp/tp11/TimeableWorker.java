package fr.upem.net.tcp.tp11;



import java.io.IOException;
import java.nio.channels.SocketChannel;

class TimeableWorker {

	private final Thread thread;
	private int inactivityCounter;
	private SocketChannel sc;
	private final Object lock = new Object();
	static private final int TIME_OUT = 300 ;
	TimeableWorker(Thread thread) {
		this.thread = thread;
		inactivityCounter = 0;
	}
	public void setSocketChannel( SocketChannel sc ){
		synchronized (lock) {
			this.sc= sc;
			inactivityCounter = 0 ;
		}
	}
	public void setActive(){
		synchronized (lock) {
			inactivityCounter = 0 ;
		}
	}
	
	void  checkTimeOut(){
		synchronized (lock) {
			if(sc ==null){
				return ;
			}
			if( inactivityCounter > TIME_OUT ){
				silentlyClose(sc);
				sc = null ;
			}
			else{
				inactivityCounter++;
			}
		}
	}

	private void silentlyClose(SocketChannel sc) {
		if (sc != null) {
			try {
				sc.close();
			} catch (IOException e) {
				// Do nothing
			}
		}
	}
}