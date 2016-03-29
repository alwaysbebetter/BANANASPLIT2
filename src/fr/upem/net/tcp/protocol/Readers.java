package fr.upem.net.tcp.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


public class Readers {
	private final static ByteBuffer BUFFINT = ByteBuffer.allocateDirect(Integer.BYTES);
	private final static ByteBuffer BUFFLONG = ByteBuffer.allocateDirect(Long.BYTES);
	
	
	/**
	 * 
	 * @param sc
	 * @param buff
	 * @return
	 * @throws IOException
	 */
	public static boolean readFully(SocketChannel sc, ByteBuffer buff)
			throws IOException {
		while(buff.hasRemaining())
		if (sc.read(buff) == -1) {
			System.err.println("Connection lost");
			return false;
		}
		return true;
	}

	/** Read an int on sc and return it.
	 * 
	 * @param sc
	 * @return
	 * @throws IOException
	 */
	public static int readInt(SocketChannel sc) throws IOException {
		BUFFINT.clear();
		if (!readFully(sc, BUFFINT)) {
			throw new ReadersException("Connection lost during readInt");
		}
		BUFFINT.flip();
		return BUFFINT.getInt();
	}
	
	/** Read a long on sc and return it.
	 * 
	 * @param sc
	 * @return
	 * @throws IOException
	 */
	public static long readLong(SocketChannel sc) throws IOException{
		BUFFLONG.clear();
		if (!readFully(sc, BUFFLONG)) {
			throw new ReadersException("Connection lost during readLong");
		}
		BUFFLONG.flip();
		return BUFFLONG.getLong();
	
	}
	
	/** 
	 * 
	 * @param sc
	 * @return
	 * @throws IOException
	 */
	public static boolean demandAccepted(SocketChannel sc) throws IOException{
		int answer = readInt(sc);
		if(answer == 1)
			return true;
		else if(answer == 2)
			return false;
		throw new ReadersException("Problem, unknow response");
	}
}
