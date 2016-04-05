package fr.upem.net.tcp.protocol;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;

import fr.upem.net.logger.Loggers;
import fr.upem.net.tcp.server.ServerTcpNonBlocking.TypePacket;

public class Writters {

	private static final Charset UTF8 = Charset.forName("utf-8");

	private static ByteBuffer allocate(int nbInt, int size) {
		return ByteBuffer.allocate((Integer.BYTES * nbInt) + size);
	}

	/**
	 * Ask to sc, if the Name is available.
	 * 
	 * @param sc
	 */
	public static void requestName(SocketChannel sc, String name)
			throws IOException {
		ByteBuffer buff = UTF8.encode(name);
		ByteBuffer buff2 = allocate(1, Byte.BYTES + buff.remaining());

		// Code 0 ask for connection
		buff2.put((byte) 0);

		// Name size in bytes. It's not name.length !
		buff2.putInt(buff.remaining());

		buff2.put(buff);

		Loggers.test(buff2);
		buff2.flip();

		sc.write(buff2);

	}

	/**
	 * Ask a private connection to the client dest.
	 * 
	 * @param src
	 * @param dest
	 */

	public static void askPrivateConnection(SocketChannel sc, long clientID,
			String src, String dest) throws IOException {
		ByteBuffer srcBuff = UTF8.encode(src);
		ByteBuffer destBuff = UTF8.encode(dest);

		ByteBuffer buff = allocate(
				2,
				Byte.BYTES + Long.BYTES + srcBuff.remaining()
						+ destBuff.remaining());

		buff.put((byte) 3).putInt(srcBuff.remaining()).put(srcBuff)
				.putLong(clientID).putInt(destBuff.remaining()).put(destBuff);

		Loggers.test(buff);
		buff.flip();

		sc.write(buff);

	}

	public static void aquitPrivateConnection(TypePacket typePacket,
			String login, int port, ByteBuffer out) {
		ByteBuffer bb = UTF8.encode(login);
		out.put((byte) typePacket.getValue());
		out.putInt(bb.remaining());
		out.put(bb);
		out.putInt(port);

	}

	/**
	 * Accept a private connection from the client scr.
	 * 
	 * @param sc
	 * @param src
	 */

	public static void acceptPrivateConnection(SocketChannel sc, long clientID,
			String src, SocketChannel privateSc) throws IOException {
		InetSocketAddress adress = (InetSocketAddress) privateSc
				.getLocalAddress();
		ByteBuffer srcBuff = UTF8.encode(src);
		ByteBuffer adressBuff = UTF8.encode(adress.getHostName());
		ByteBuffer buff = allocate(
				3,
				Byte.BYTES + Long.BYTES + srcBuff.remaining()
						+ adressBuff.remaining());

		buff.put((byte) 5).putInt(srcBuff.remaining()).put(srcBuff)
				.putLong(clientID).putInt(adressBuff.remaining())
				.put(adressBuff);

		buff.putInt(adress.getPort());

		Loggers.test(buff);
		buff.flip();

		sc.write(buff);
	}

	/**
	 * Deny a private connection from src.
	 * 
	 * @param sc
	 * @param src
	 * @throws IOException
	 */

	public static void denyPrivateConnection(SocketChannel sc, long clientID,
			String src) throws IOException {
		ByteBuffer srcBuff = UTF8.encode(src);

		ByteBuffer buff = allocate(1,
				Byte.BYTES + Long.BYTES + srcBuff.remaining());

		buff.put((byte) 6).putInt(srcBuff.remaining()).put(srcBuff)
				.putLong(clientID);

		Loggers.test(buff);
		buff.flip();

		sc.write(buff);
	}

	/**
	 * Ask for a connection to send files.
	 * 
	 * @param sc
	 *            The socket use for file.
	 * @param type
	 *            9 for asking and 10 to respond.
	 */
	public static void askPrivateFileConnection(SocketChannel sc, byte type,
			SocketChannel fileChannel) throws IOException,
			IllegalStateException {
		if ((type == (byte) 9) || (type == (byte) 10)) {

			ByteBuffer adressBuff = UTF8.encode(fileChannel.getLocalAddress()
					.toString());

			ByteBuffer buff = allocate(1, Byte.BYTES + adressBuff.remaining());

			buff.put(type).putInt(adressBuff.remaining()).put(adressBuff);

			Loggers.test(buff);
			buff.flip();
			sc.write(buff);
		} else {
			throw new IllegalStateException("Wrong request id " + type
					+ ". Type is 9 or 10.");
		}

	}

	/**
	 * Ask authorization to send a file.
	 * 
	 * @param sc
	 */
	public static void askToSendFile(SocketChannel sc, String fileName)
			throws IOException {

		ByteBuffer fileBuff = UTF8.encode(fileName);

		ByteBuffer buff = allocate(1, Byte.BYTES + fileBuff.remaining());

		buff.put((byte) 11).putInt(fileBuff.remaining()).put(fileBuff);

		Loggers.test(buff);

		buff.flip();

		sc.write(buff);
	}

	/**
	 * Send agreement for file.
	 * 
	 * @param sc
	 * @throws IOException
	 */
	public static void acceptFile(SocketChannel sc) throws IOException {
		sc.write(ByteBuffer.allocate(Byte.BYTES).put((byte) 12));
	}

	/**
	 * Send disagreement for file.
	 * 
	 * @param sc
	 * @throws IOException
	 */
	public static void refuseFile(SocketChannel sc) throws IOException {
		sc.write(ByteBuffer.allocate(Byte.BYTES).put((byte) 13));
	}

	/**
	 * Send a file.
	 * 
	 * @param sc
	 * @param path
	 * @throws IOException
	 */
	public static void sendFile(SocketChannel sc, Path path) throws IOException {
		// TODO Fix max size of buff
		FileInputStream fIn;
		FileChannel fChan;
		long fSize;
		ByteBuffer buff;
		fIn = new FileInputStream(path.getFileName().toString());
		fChan = fIn.getChannel();
		fSize = fChan.size();
		buff = allocate(0, Byte.BYTES + Long.BYTES +(int)fSize);
		buff.put((byte)14).putLong(fSize);
		while(fChan.read(buff) != -1);		
		buff.flip();
		
		fChan.close();
		fIn.close();
		sc.write(buff);
	}

	/**
	 * 
	 * @param sc
	 * @param expediteur
	 */

	public static void sendMessage(SocketChannel sc, long clientID, String src,
			String msg) throws IOException {
		ByteBuffer srcBuff = UTF8.encode(src);
		ByteBuffer msgBuff = UTF8.encode(msg);

		ByteBuffer buff = allocate(
				2,
				Byte.BYTES + Long.BYTES + srcBuff.remaining()
						+ msgBuff.remaining());

		buff.put((byte) 15).putInt(srcBuff.remaining()).put(srcBuff)
				.putLong(clientID).putInt(msgBuff.remaining()).put(msgBuff);

		Loggers.testChatMessage(buff);

		buff.flip();

		sc.write(buff);
	}

	/**
	 * Send a private message
	 * 
	 * @param sc
	 *            The privateChannel
	 * @param src
	 *            The name of the sender
	 * @param msg
	 *            The message to send
	 * @throws IOException
	 */
	public static void sendPrivateMessage(SocketChannel sc, String src,
			String msg) throws IOException {
		ByteBuffer srcBuff = UTF8.encode(src);
		ByteBuffer msgBuff = UTF8.encode(msg);

		ByteBuffer buff = allocate(2, Byte.BYTES + srcBuff.remaining()
				+ msgBuff.remaining());

		buff.put((byte) 15).putInt(srcBuff.remaining()).put(srcBuff)
				.putInt(msgBuff.remaining()).put(msgBuff);

		Loggers.test(buff);

		buff.flip();

		sc.write(buff);
	}

	public static void sendSimpleMessage(SocketChannel sc, String msg)
			throws IOException {

		ByteBuffer msgBuff = UTF8.encode(msg);

		ByteBuffer buff = ByteBuffer.allocate(1024);

		buff.putInt(msgBuff.remaining()).put(msgBuff);

		buff.flip();

		sc.write(buff);
	}

}
