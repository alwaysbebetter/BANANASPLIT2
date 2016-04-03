package fr.upem.net.tcp.tp11;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ServerEcho2 {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	static private final int BUFSIZ = 1024;

	private class Attachement {
		ByteBuffer buff;
		boolean isClosed = false;
		ArrayBlockingQueue<ByteBuffer> queue = new ArrayBlockingQueue<ByteBuffer>(
				BUFSIZ);

		public Attachement() {

			buff = ByteBuffer.allocate(BUFSIZ);
		}

		public int getInterest() {
			int interest = 0;// initialize
			if (buff.position() > 0) {
				interest = interest | SelectionKey.OP_WRITE;
			}
			if (!isClosed) {
				interest |= SelectionKey.OP_READ;
			}
			return interest;

		}
	}

	public ServerEcho2(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		while (!Thread.interrupted()) {

			selector.select();

			processSelectedKeys();
			selectedKeys.clear();
		}
	}

	private void processSelectedKeys() throws IOException {
		for (SelectionKey key : selectedKeys) {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);// on ne catrch pas cette exception parce que si
								// le accept
				// pete c'et que le serveur est mor
			}
			try { // on la catch ici car on arrete pas le serveur pour ça
				if (key.isValid() && key.isWritable()) {
					doWrite(key);
				}
				if (key.isValid() && key.isReadable()) {
					doRead(key);
				}
			} catch (IOException e) {
				;
			}
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		// only the ServerSocketChannel is register in OP_ACCEPT
		SocketChannel sc = serverSocketChannel.accept();
		if (sc == null)
			return; // In case, the selector gave a bad hint
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE,
				new Attachement());

	}

	private void doRead(SelectionKey key) throws IOException {
		Attachement theAttachement = (Attachement) key.attachment();

		SocketChannel client = (SocketChannel) key.channel();

		if (-1 == client.read(theAttachement.buff)) {

			theAttachement.isClosed = true;

			// si il a deja finis d'écrire ce n'est pas la peine
			// il a
			if (theAttachement.buff.position() == 0) {
				client.close();
			}

		}

		key.interestOps(theAttachement.getInterest());
	}

	private void doWrite(SelectionKey key) throws IOException {
		SocketChannel client = (SocketChannel) key.channel();
		Attachement theAttachement = (Attachement) key.attachment();
		// theAttachement.buff.flip();
		for (SelectionKey selectionKey : selector.keys()) {
			if (!client.equals(selectionKey.channel())) {
				Attachement at = (Attachement) selectionKey.attachment();
				System.out.println("coucou");
				if (at != null) {
					System.out.println("coucou2");
					theAttachement.buff.flip();
					ByteBuffer bb = ByteBuffer.allocate(theAttachement.buff
							.remaining());
					bb.put(theAttachement.buff);
					at.queue.add(bb);
				}
			}
		}

		while ( ! theAttachement.queue.isEmpty() ){
			ByteBuffer tmp = theAttachement.queue.poll() ;
			tmp.flip();
			client.write(tmp);
		}
		theAttachement.buff.flip();
		client.write(theAttachement.buff);
		theAttachement.buff.compact();// pour bien se repositionner sans ecraser
										// ce que l'on a lu
		if (theAttachement.isClosed) {
			client.close();
			theAttachement.isClosed = true;
		}

		key.interestOps(theAttachement.getInterest());
	}

	public static void main(String[] args) throws NumberFormatException,
			IOException {
		new ServerEcho2(Integer.parseInt(args[0])).launch();

	}

}