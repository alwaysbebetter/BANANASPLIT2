package fr.upem.net.tcp.server;

import java.io.IOException;
import java.io.UTFDataFormatException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;



public class ServerMultiChatTCPNonBlockingWithQueueGoToMatou2 {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final ConcurrentHashMap<Integer, SelectionKey> map = new ConcurrentHashMap<>();
	private int co = 0;
	static private final int BUFSIZ = 200;
	public static final Charset UTF_8 = Charset.forName("utf-8");
	public final static int SRC_DATA = 0, DEST_DATA = 1, DEST_DATA_SRC = 2;

private enum StatusTreatment {

	TYPE_READING, TYPE_KNOWN, CHOOSE_TREATING, READ_LOGIN, END_READING, END_TREATMENT, REFILL, ERROR, DONE

}
	private class Attachement {
		
		ByteBuffer buff;
		boolean isClosed = false;
		LinkedList<ByteBuffer> queue = new LinkedList<>();
		public Reader reader;
		public DataPacketRead dataPacketRead;

		public Attachement() {

			buff = ByteBuffer.allocate(BUFSIZ*4);
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

	public ServerMultiChatTCPNonBlockingWithQueueGoToMatou2(int port) throws IOException {
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
			Attachement theAttachement = (Attachement) key.attachment();

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
		map.put(co++,
				sc.register(selector, SelectionKey.OP_READ
						| SelectionKey.OP_WRITE, new Attachement()));

	}

	private void publish(SelectionKey key, Attachement theAttachement)
			throws IOException {
		for (SelectionKey key2 : selector.keys()) {
			if (key2.isValid() && ( key2.channel() instanceof SocketChannel ) && ( !key2.equals(key))) {
				theAttachement.buff.flip();
				SocketChannel sch = (SocketChannel) key2.channel();
				sch.write(theAttachement.buff);
				
			}

		}
	}
	
	
	private void doRead(SelectionKey key) throws IOException {
		Attachement theAttachement = (Attachement) key.attachment();

		SocketChannel client = (SocketChannel) key.channel();

		if (-1 == client.read(theAttachement.buff)) {
			theAttachement.isClosed = true;
			if (theAttachement.buff.position() == 0) {

				client.close();
			}

		}

		if( theAttachement.reader == null ){
			theAttachement.reader = new ReaderString(SRC_DATA);
		}
		switch (theAttachement.reader.process(theAttachement.buff)) {
		case DONE:
			theAttachement.dataPacketRead = theAttachement.reader.get();
			theAttachement.buff.clear();
			theAttachement.buff.putInt(theAttachement.dataPacketRead.getSizeLoginSrc());
			theAttachement.buff.put(UTF_8.encode(theAttachement.dataPacketRead.getLoginSrc()));
			//reset Datzpz
			//dataPacketRead.setTypePacket(typeLastPacketReceiv);
			break;
		case ERROR:
			// TODO : close
			break;
		case REFILL:
			return;
		}


		key.interestOps(theAttachement.getInterest());
	}

	

	private void doWrite(SelectionKey key) throws IOException {
		SocketChannel client = (SocketChannel) key.channel();
		Attachement theAttachement = (Attachement) key.attachment();
		
		// faire le techeck sur la taille avant et il fatu faire en sorte que la taille n'excede jamasi celel du buffer qu'on a allouer comme ça pas besoin de reallouer.
		

		publish(key, theAttachement);

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
		new ServerMultiChatTCPNonBlockingWithQueueGoToMatou2(Integer.parseInt(args[0])).launch();

	}

}