package fr.upem.net.tcp.tp11;




// WITH THAT VERSION, BECAREFUL WITH THE ORDER 
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class TimeableWorkerLockFree {

	private final Thread thread;
	private final AtomicInteger inactivityCounter;
	volatile private SocketChannel sc;
	// lock deleted
	static private final int TIME_OUT = 300;

	TimeableWorkerLockFree(Thread thread) {
		this.thread = thread;
		inactivityCounter = new AtomicInteger(0);
	}

	public void setSocketChannel(SocketChannel sc) {
		// THIS ORDER IS IMPORTANT !!! VOLATILE garantie dans cette ordre que la
		// si on list le sc ça nous garantie que le inative avant vaux 0 !!!
		// cette ordre est super important
		inactivityCounter.set(0);
		this.sc = sc;

	}

	public void setActive() {
		inactivityCounter.set(0);
	}

	void checkTimeOut() {

		if (sc == null) {
			return;
		}
		SocketChannel toClose = sc ;
		int before, after;

		// THIS BLOC INCREMENT WITH 1 !!!!
		do {
			before = inactivityCounter.get();
			after = before + 1;
		} while (!inactivityCounter.compareAndSet(before, after));
		
		
		if (before > TIME_OUT) {
			silentlyClose(toClose);

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