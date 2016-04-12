package fr.upem.net.tcp.protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;

import fr.upem.net.logger.Loggers;
import fr.upem.net.tcp.server.ServerMultiChatTCPNonBlockingWithQueueGoToMatou3.TypePacket;

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
		buff2.put((byte)TypePacket.ASC_CO_SERV.getValue());

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

	/**
	 * Accept a private connection from the client scr.
	 * 
	 * @param sc
	 * @param src
	 */

	public static void acceptPrivateConnection(SocketChannel sc, long clientID,
			String src, ServerSocketChannel ssc) throws IOException {
		ServerSocket socket =  ssc.socket();

		ByteBuffer srcBuff = UTF8.encode(src);

		ByteBuffer adressBuff = UTF8.encode(socket.getInetAddress().getHostAddress());

		ByteBuffer buff = allocate(
				3,
				Byte.BYTES + Long.BYTES + srcBuff.remaining()
						+ adressBuff.remaining());

		buff.put((byte)TypePacket.ACC_CO_PRV_CS.getValue()).putInt(srcBuff.remaining()).put(srcBuff)
				.putLong(clientID).putInt(adressBuff.remaining())
				.put(adressBuff);

		buff.putInt(socket.getLocalPort());

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

		buff.put((byte)TypePacket.REF_CO_PRV_CS.getValue()).putInt(srcBuff.remaining()).put(srcBuff)
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
	public static void askPrivateFileConnection(SocketChannel sc, TypePacket type,
			ServerSocketChannel fileChannel) throws IOException,
			IllegalStateException {
		if ((type == TypePacket.ASC_CO_FIL_CC) || (type == TypePacket.ACC_CO_FIL_CC)) {
			InetSocketAddress adress = (InetSocketAddress) fileChannel
					.getLocalAddress();

			ByteBuffer adressBuff = UTF8.encode(adress.getHostName());

			ByteBuffer buff = allocate(2, Byte.BYTES + adressBuff.remaining());

			buff.put((byte)type.getValue()).putInt(adressBuff.remaining()).put(adressBuff).putInt(adress.getPort());

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
	public static void askToSendFile(SocketChannel sc, Path path)
			throws IOException {
		
		File file = path.toFile();

		ByteBuffer fileBuff = UTF8.encode(file.getName());

		ByteBuffer buff = allocate(1, Long.BYTES + Byte.BYTES + fileBuff.remaining());

		buff.put((byte) TypePacket.ASC_SEND_FIL_CC.getValue()).putLong(file.length()).putInt(fileBuff.remaining()).put(fileBuff);

		Loggers.test(buff);

		buff.flip();;

		sc.write(buff);
	}

	/**
	 * Send agreement for file.
	 * 
	 * @param sc
	 * @throws IOException
	 */
	public static void acceptFile(SocketChannel sc) throws IOException {
		ByteBuffer buff = ByteBuffer.allocate(Byte.BYTES).put((byte) TypePacket.ACC_SEND_FIL_CC.getValue());
		Loggers.test(buff);
		buff.flip();
		sc.write(buff);
	}

	/**
	 * Send disagreement for file.
	 * 
	 * @param sc
	 * @throws IOException
	 */
	public static void refuseFile(SocketChannel sc) throws IOException {
		ByteBuffer buff = ByteBuffer.allocate(Byte.BYTES).put((byte) TypePacket.REF_SEND_FIL_CC.getValue());
		Loggers.test(buff);
		buff.flip();
		sc.write(buff);
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
		fIn = new FileInputStream(path.toFile());
		fChan = fIn.getChannel();
		fSize = fChan.size();
		buff = allocate(0, Byte.BYTES + Long.BYTES +(int)fSize);
		buff.put((byte)14).putLong(fSize);
		int read;
		while((read = fChan.read(buff)) != 0){
			if(read == -1)
				break;
		}

		fChan.close();
		fIn.close();
		Loggers.test(buff);
		buff.flip();
		sc.write(buff);
	}

	/**
	 * 
	 * @param sc
	 * @param expediteur
	 */

	public static void sendMessage(SocketChannel sc, long clientID, String src,
			String msg) throws IOException {
		//Avoid flood
		if(msg.isEmpty())
			return;
		ByteBuffer srcBuff = UTF8.encode(src);
		ByteBuffer msgBuff = UTF8.encode(msg);

		ByteBuffer buff = allocate(
				2,
				Byte.BYTES + Long.BYTES + srcBuff.remaining()
						+ msgBuff.remaining());

		buff.put((byte) TypePacket.MESSAGE.getValue()).putInt(srcBuff.remaining()).put(srcBuff)
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
