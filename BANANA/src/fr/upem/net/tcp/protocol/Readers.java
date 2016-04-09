package fr.upem.net.tcp.protocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;

import fr.upem.net.logger.Loggers;


public class Readers {
	
	//The class Charset is thread-safe as the javadoc says
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
		ByteBuffer buffInt = ByteBuffer.allocateDirect(Integer.BYTES);
		buffInt.clear();
		if (!readFully(sc, buffInt)) {
			throw new ReadersException("Connection lost during readInt");
		}
		buffInt.flip();
		return buffInt.getInt();
	}
	
	/** Read an int on sc and return it.
	 * 
	 * @param sc
	 * @return
	 * @throws IOException
	 */
	public static byte readByte(SocketChannel sc) throws IOException {
		ByteBuffer buff = ByteBuffer.allocateDirect(Byte.BYTES);
		buff.clear();
		if (!readFully(sc, buff)) {
			throw new ReadersException("Connection lost during readByte");
		}
		buff.flip();
		return buff.get();
	}
	
	/** Read a long on sc and return it.
	 * 
	 * @param sc
	 * @return
	 * @throws IOException
	 */
	public static long readLong(SocketChannel sc) throws IOException{
		ByteBuffer buffLong = ByteBuffer.allocateDirect(Long.BYTES);
		buffLong.clear();
		if (!readFully(sc, buffLong)) {
			throw new ReadersException("Connection lost during readLong");
		}
		buffLong.flip();
		return buffLong.getLong();
	
	}
	
	/** 
	 * 
	 * @param sc
	 * @return
	 * @throws IOException
	 */
	public static boolean nameAccepted(SocketChannel sc) throws IOException{
		byte answer = readByte(sc);
		if(answer == (byte)1)
			return true;
		else if(answer == (byte)2)
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


		InetSocketAddress inet = new InetSocketAddress(adress,port);

		return inet ;


	
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
	
	public static void readPrivateMessage(SocketChannel sc) throws IOException{
		System.out.print("(privé) ");
		readMessage(sc);
	}
	
	public static void readSimpleMessage(SocketChannel sc) throws IOException{
		//TODO Erase method after test

		int msgSize = readInt(sc);
		ByteBuffer buff = ByteBuffer.allocate(msgSize);
		if (!readFully(sc, buff)) {
			throw new ReadersException("Connection lost during readMessage");
		}
		buff.flip();
		String message = UTF8.decode(buff).toString();
		
		System.out.println("Message reçu : " + message);
	}
	
	public static void readFile(SocketChannel sc, String fileName) throws IOException{
		byte id = readByte(sc);
		long size = readLong(sc);
		int count =0 ;
		Path path = Paths.get(fileName);
		File file = path.toFile();
		ByteBuffer buff = ByteBuffer.allocate(Byte.BYTES + Long.BYTES + (int)size);
		//Data use for debug with logger
		buff.put(id).putLong(size);
		if(!readFully(sc,buff)){
			throw new ReadersException("Connection lost during readFile");
		}
		String newFileName;
		
		//If we can't create file, we just change the name by adding a number
		while(!file.createNewFile()){
			newFileName = count + path.getFileName().toString();
			file = new File(newFileName);
			count++;
		}
		System.out.println(file.getAbsolutePath());
		Loggers.test(buff);
		buff.flip();
		//Ignore first data, just write byte of file
		buff.position(Byte.BYTES + Long.BYTES);
		FileOutputStream fi = new FileOutputStream(file);
		FileChannel fc = fi.getChannel();
		
		while(buff.hasRemaining()){
			fc.write(buff);
		}
		
		fc.close();
		fi.close();
	}
}


