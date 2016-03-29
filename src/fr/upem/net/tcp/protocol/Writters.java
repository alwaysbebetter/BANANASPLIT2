package fr.upem.net.tcp.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;

public class Writters {
	
	private static final Charset UTF8 = Charset.forName("utf-8");
	
	/** Attente active !!
	 * 
	 * @param sc
	 * @param buff
	 * @throws IOException
	 */
	public static void writeFully(SocketChannel sc,ByteBuffer buff) throws IOException{
		
		while(buff.hasRemaining()){
			sc.write(buff);
		}
	}
	
	
	/** Ask to sc, if the Name is available.
	 * 
	 * @param sc
	 */
	public static void requestName(SocketChannel sc, String name) throws IOException{
		ByteBuffer buff = UTF8.encode(name);
		ByteBuffer buff2 = ByteBuffer.allocate(Integer.BYTES*2 + buff.remaining());
		
		//Code 0 ask for connection
		buff2.putInt(0);
		
		//Name size in bytes. It's not name.length !
		buff2.putInt(buff.remaining());
		
		buff2.put(buff);
		
		sc.write(buff2);
		
		
	}
	
	/**
	 * 
	 * @param sc
	 */

	public static void accept(SocketChannel sc){
		
	}
	
	/**
	 * 
	 * @param sc
	 */
	public static void deny(SocketChannel sc){
		
	}
	
	/**
	 * 
	 * @param sc
	 * @param expediteur
	 */
	public static void sendMessage(SocketChannel sc, String expediteur, String msg){
		
	}
	
	/**
	 * 
	 * @param sc
	 * @param path
	 */
	public static void sendFile(SocketChannel sc, Path path){
		
	}

	/**
	 * 
	 * @param src
	 * @param dst
	 */
	public static void sendAdress(SocketChannel src, SocketChannel dst){
		
	}

}
