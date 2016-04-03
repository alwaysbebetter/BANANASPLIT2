package fr.upem.net.tcp.protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;

import fr.upem.net.logger.Loggers;
import fr.upem.net.tcp.server.ServerTcpNonBlocking.TypePacket;

public class Writters {
	
	private static final Charset UTF_8 = Charset.forName("utf-8");
	
	
	private static ByteBuffer allocate(int nbInt, int size){
		return ByteBuffer.allocate(  (Integer.BYTES * nbInt) + size);
	}
	
	/** Ask to sc, if the Name is available.
	 * 
	 * @param sc
	 */
	public static void requestName(SocketChannel sc, String name) throws IOException{
		ByteBuffer buff = UTF_8.encode(name);
		ByteBuffer buff2 = allocate(2 , buff.remaining());
		
		//Code 0 ask for connection
		buff2.putInt(0);
		
		//Name size in bytes. It's not name.length !
		buff2.putInt(buff.remaining());
		
		buff2.put(buff);
		
		Loggers.test(buff2);
		buff2.flip();

		
		sc.write(buff2);
		
	}
	
	/**
	 * Ask a private connection to the client dest.
	 * @param src
	 * @param dest
	 */
	public static void askPrivateConnection(SocketChannel sc, String src, String dest)throws IOException{
		ByteBuffer srcBuff = UTF_8.encode(src);
		ByteBuffer destBuff = UTF_8.encode(dest);

		ByteBuffer buff = allocate(3,srcBuff.remaining() + destBuff.remaining() );
		
		buff.putInt(3).putInt(srcBuff.remaining()).put(srcBuff).putInt(destBuff.remaining()).put(destBuff);
		
		Loggers.test(buff);
		buff.flip();
		
		sc.write(buff);
		
	}
	
	public static void aquitPrivateConnection(TypePacket typePacket, String login , int port , ByteBuffer out ){
		ByteBuffer bb = UTF_8.encode(login);
		out.put((byte)typePacket.getValue());
		out.putInt(bb.remaining());
		out.put(bb);
		out.putInt(port);
		
		
	}
	/**
	 * Accept a private connection from the client scr.
	 * @param sc
	 * @param src
	 */
	public static void acceptPrivateConnection(SocketChannel sc,String src) throws IOException{
		InetSocketAddress adress = (InetSocketAddress)sc.getLocalAddress();
		ByteBuffer srcBuff = UTF_8.encode(src);
		ByteBuffer adressBuff = UTF_8.encode(adress.getHostName());
		ByteBuffer buff = allocate(4,srcBuff.remaining() + adressBuff.remaining());
		
		buff.putInt(5).putInt(srcBuff.remaining()).put(srcBuff).putInt(adressBuff.remaining()).put(adressBuff);
		buff.putInt(adress.getPort());
		
		Loggers.test(buff);
		buff.flip();
		
		sc.write(buff);
	}
	
	/**
	 * Deny a private connection from src.
	 * @param sc
	 * @param src
	 * @throws IOException
	 */
	public static void denyPrivateConnection(SocketChannel sc,String src) throws IOException{
		ByteBuffer srcBuff = UTF_8.encode(src);

		ByteBuffer buff = allocate(2,srcBuff.remaining() );
		
		buff.putInt(6).putInt(srcBuff.remaining()).put(srcBuff);
		
		Loggers.test(buff);
		buff.flip();
		
		sc.write(buff);
	}
	
	/**
	 * Ask for a connection to send files.
	 * @param sc The socket use for file.
	 * @param type 9 for asking and 10 to respond.
	 */
	public static void askPrivateFileConnection(SocketChannel sc,int type) throws IOException, IllegalStateException{
		if( (type == 9) || (type == 10)){
			ByteBuffer adressBuff = UTF_8.encode(sc.getLocalAddress().toString());
			ByteBuffer buff = allocate(2,adressBuff.remaining());
			
			buff.putInt(type).putInt(adressBuff.remaining()).put(adressBuff);
			
			Loggers.test(buff);
			buff.flip();
			sc.write(buff);
		}
		else{
			throw new IllegalStateException("Wrong request id "+type+". Type is 9 or 10.");
		}
		

	}
	
	/**
	 * Ask authorization to send a file.
	 * @param sc
	 */
	public static void askToSendFile(SocketChannel sc, Path path){
		System.out.println("askTosendFile");
	}
	
	/**
	 * Send acceptation for file.
	 * @param sc
	 * @throws IOException
	 */
	public static void acceptFile(SocketChannel sc) throws IOException{
		sc.write(ByteBuffer.allocate(Integer.BYTES).putInt(12));
	}
	
	public static void refuseFile(SocketChannel sc) throws IOException{
		sc.write(ByteBuffer.allocate(Integer.BYTES).putInt(12));
	}
	
	/**
	 * 
	 * @param sc
	 * @param expediteur
	 */
	public static void sendMessage(SocketChannel sc, String src, String msg) throws IOException{
		ByteBuffer srcBuff = UTF_8.encode(src);
		ByteBuffer msgBuff = UTF_8.encode(msg);

		ByteBuffer buff = allocate(3,srcBuff.remaining() + msgBuff.remaining() );
		
		buff.putInt(15).putInt(srcBuff.remaining()).put(srcBuff).putInt(msgBuff.remaining()).put(msgBuff);
		
		Loggers.test(buff);
		
		buff.flip();
		
		sc.write(buff);
	}
	
	/**
	 * 
	 * @param sc
	 * @param path
	 */
	public static void sendFile(SocketChannel sc, Path path){
		//TODO
	}

	

}
