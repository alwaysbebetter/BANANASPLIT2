package fr.upem.net.tcp.tp11;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Scanner;

public class ClientExchange {

	private final SocketChannel socket;
	private static final Charset UTF_8 = Charset.forName("utf-8");
	private ByteBuffer out = ByteBuffer.allocate(1024);

	public ClientExchange(String hostName, int port) throws IOException {
		socket = SocketChannel.open();
		socket.connect(new InetSocketAddress(hostName, port));
	}
	/*
	 *  you must allocate the good size 
	 */
	public static boolean readfully(ByteBuffer bb, SocketChannel sc) throws IOException {
		while (bb.hasRemaining()) {
			if (sc.read(bb) == -1)
				return false;
		}
		return true;
	}
	
	private void send(Scanner scan) throws IOException {
		ByteBuffer tmp = UTF_8.encode(scan.nextLine());
		
		out.clear();
		
		out.putInt(tmp.remaining()).put(tmp);
		out.flip();
		socket.write(out);
	
	}

	private ByteBuffer receiv(ByteBuffer bbSize) throws IOException {
		bbSize.clear();
	
		while (!readfully(bbSize,socket));
		System.out.println("coucou");
		ByteBuffer in = ByteBuffer.allocate(bbSize.getInt());
		while (!readfully(in,socket));
		return in;
	}
	public void launch() throws IOException {
		ByteBuffer bbSize = ByteBuffer.allocate(Integer.BYTES);
		try (Scanner scan = new Scanner(System.in)) {
			while (scan.hasNextLine()) {
				send(scan);
				ByteBuffer in = receiv(bbSize);
				System.out.println(UTF_8.decode(in));
			}
		}finally{
			silentlyClose(socket);
		}
	}
	

	public void silentlyClose(SocketChannel sc) {
		if (sc != null) {
			try {
				sc.close();
			} catch (Exception e) {
				// ignore
			}

		}
	}

	public static void main(String[] args) throws NumberFormatException,
			IOException {

		if (args.length != 2) {
			System.out.println("ClientExchange Postname Sort");
		}
		new ClientExchange(args[0], Integer.parseInt(args[1])).launch();
	}
}
