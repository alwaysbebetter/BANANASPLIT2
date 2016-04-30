package fr.upem.net.tcp.clientProtocol;

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
import fr.upem.net.tcp.server.ServerTCP.TypePacket;


public class Readers {
	
	//The class Charset is thread-safe as the javadoc says
	private final static Charset UTF8 = Charset.forName("utf-8");
	
	
	
	private static boolean readFully(SocketChannel sc, ByteBuffer buff)
			throws IOException {
		while(buff.hasRemaining())
		if (sc.read(buff) == -1) {;
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
		ByteBuffer buffInt = ByteBuffer.allocate(Integer.BYTES);
		buffInt.clear();
		if (!readFully(sc, buffInt)) {
			throw new IOException("Connection lost during readInt");
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
		ByteBuffer buff = ByteBuffer.allocate(Byte.BYTES);
		buff.clear();
		if (!readFully(sc, buff)) {
			throw new IOException("Connection lost during readByte");
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

		ByteBuffer buffLong = ByteBuffer.allocate(Long.BYTES);

		buffLong.clear();
		if (!readFully(sc, buffLong)) {
			throw new IOException("Connection lost during readLong");
		}
		buffLong.flip();
		return buffLong.getLong();
	
	}
	/**
	 * Read a length (int) and the number of byte read.
	 * @param sc
	 * @return The string decode in utf8.
	 * @throws IOException
	 */
	public static String readString(SocketChannel sc) throws IOException{
		int stringSize = readInt(sc);

		ByteBuffer buff = ByteBuffer.allocate(stringSize);
		if (!readFully(sc, buff)) {
			throw new IOException("Connection lost during readAdress");
		}
		buff.flip();
		String string = UTF8.decode(buff).toString();
		return string;
	}
	
	/** 
	 * 
	 * @param sc
	 * @return
	 * @throws IOException
	 */
	public static boolean nameAccepted(SocketChannel sc) throws IOException{

		
		TypePacket packet = TypePacket.values()[readByte(sc)];
		if(packet == TypePacket.ACC_CO_SERV)
			return true;
		else if(packet == TypePacket.REF_CO_SERV)

			return false;
		throw new IOException("Problem, unknow response");
	}

	/**
	 * Read the fileName of a demand or the name of a demander.
	 * @param sc
	 * @return The pseudo of the demander.
	 * @throws IOException
	 */
	public static String readDemand(SocketChannel sc) throws IOException{
		
		String pseudo = readString(sc);

		return pseudo;
	}
	/**

	 * Return a SocketChannel connected to the address and port read.
	 * @param sc
	 * @return
	 * @throws IOException
	 */
	public static InetSocketAddress readAddress(SocketChannel sc) throws IOException{
		
		String adress = readString(sc);

		
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
		
		String pseudo = readString(sc);
		
		
		String message = readString(sc);
		
		System.out.println(pseudo +" : " + message);
		
		
	}
	
	public static void readPrivateMessage(SocketChannel sc) throws IOException{
		System.out.print("(privé) ");
		readMessage(sc);
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
			throw new IOException("Connection lost during readFile");
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


