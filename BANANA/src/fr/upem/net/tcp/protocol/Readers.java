package fr.upem.net.tcp.protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;


public class Readers {
	private final static ByteBuffer BUFFINT = ByteBuffer.allocateDirect(Integer.BYTES);
	private final static ByteBuffer BUFFLONG = ByteBuffer.allocateDirect(Long.BYTES);
	private final static Charset UTF8 = Charset.forName("utf-8");
	
	
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
	public static boolean nameAccepted(SocketChannel sc) throws IOException{
		int answer = readInt(sc);
		if(answer == 1)
			return true;
		else if(answer == 2)
			return false;
		throw new ReadersException("Problem, unknow response");
	}

	/**
	 * Read the fileName of a demand or the name of a demander.
	 * @param sc
	 * @return The pseudo of the demander.
	 * @throws IOException
	 */
	public static String readDemand(SocketChannel sc) throws IOException{
		int pseudoSize = readInt(sc);
		
		ByteBuffer buff = ByteBuffer.allocate(pseudoSize);
		if (!readFully(sc, buff)) {
			throw new ReadersException("Connection lost during readDemandConnection");
		}
		buff.flip();
		String pseudo = UTF8.decode(buff).toString();

		return pseudo;
	}
	/**
	 * Return a SocketChannel connected to the address and port read.
	 * @param sc
	 * @return
	 * @throws IOException
	 */
	public static InetSocketAddress readAddress(SocketChannel sc) throws IOException{
		int adressSize = readInt(sc);
		
		ByteBuffer buff = ByteBuffer.allocate(adressSize);
		if (!readFully(sc, buff)) {
			throw new ReadersException("Connection lost during readAdress");
		}
		buff.flip();
		String adress = UTF8.decode(buff).toString();
		
		int port = readInt(sc);


		return new InetSocketAddress(adress,port);

	
	}
	
	
	/**
	 * Read a message and print.
	 * @param sc
	 * @throws IOException
	 */
	public static void readMessage(SocketChannel sc) throws IOException{
		int pseudoSize = readInt(sc);
		
		ByteBuffer buff = ByteBuffer.allocate(pseudoSize);
		if (!readFully(sc, buff)) {
			throw new ReadersException("Connection lost during readMessage");
		}
		buff.flip();
		String pseudo = UTF8.decode(buff).toString();
		
		int msgSize = readInt(sc);
		buff = ByteBuffer.allocate(msgSize);
		if (!readFully(sc, buff)) {
			throw new ReadersException("Connection lost during readMessage");
		}
		buff.flip();
		String message = UTF8.decode(buff).toString();
		
		System.out.println(pseudo +" : " + message);
		
		
	}
}


