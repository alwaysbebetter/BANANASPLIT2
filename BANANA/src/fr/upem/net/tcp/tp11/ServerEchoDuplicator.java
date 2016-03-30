package fr.upem.net.tcp.tp11;



import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Set;

public class ServerEchoDuplicator {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	static private final int BUFSIZ = 10;

	private class Attachement {
		ByteBuffer in;
		ByteBuffer out;

		boolean isClosed = false;
		SelectionKey key;

		public Attachement() {
			in = ByteBuffer.allocate(BUFSIZ);
			out = ByteBuffer.allocate(BUFSIZ);
		}
		
		

		public int getInterest() { // il faut le faire aprés avoir unappeler process ET ça il faut le préciser dans le protocole.
			int interest = 0;// initialize
			if (out.position() > 0) {
				interest = interest | SelectionKey.OP_WRITE;
			}
			if (!isClosed && in.hasRemaining()) {
				interest |= SelectionKey.OP_READ;
			}
			return interest;

		}

		public void buildOut() {

			if (in.position() >= 2) {
				do {
					in.flip();
					byte b = in.get();
					byte b2 = in.get();
					if ( ( (b2 -b) == 1 ) && out.hasRemaining()) {
						out.put(b);
					}
					in.position(in.position()-1);
					in.compact();
				} while (in.position() > 2 );
			}
			
		}

	}

	public ServerEchoDuplicator(int port) throws IOException {
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
			printKeys();
			System.out.println("Starting select");
			selector.select();
			System.out.println("Select finished");
			printSelectedKey();
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
		sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, new Attachement());

	}

	// on n'utilise pas de readfully en non bloquant.

	private void doRead(SelectionKey key) throws IOException {
		
		
		Attachement theAttachement = (Attachement) key.attachment();


		SocketChannel client = (SocketChannel) key.channel();

		if (-1 == client.read(theAttachement.in)) {

			theAttachement.isClosed = true;

			if (theAttachement.in.position() == 0) {
				client.close();
			}

		}
		
		
		theAttachement.buildOut();
	
		key.interestOps(theAttachement.getInterest());

	}

	private void doWrite(SelectionKey key) throws IOException {
		SocketChannel client = (SocketChannel) key.channel();
		Attachement theAttachement = (Attachement) key.attachment();


		
		
		
		theAttachement.out.flip();
		
		client.write(theAttachement.out);
		
		theAttachement.out.compact();
		
		if (theAttachement.isClosed) {
			client.close();
			theAttachement.isClosed = true;
		}

		key.interestOps(theAttachement.getInterest());// ça ça permet le multi
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		new ServerEchoDuplicator(Integer.parseInt(args[0])).launch();

	}

	/***
	 * Theses methods are here to help understanding the behavior of the
	 * selector
	 ***/

	private String interestOpsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps & SelectionKey.OP_ACCEPT) != 0)
			list.add("OP_ACCEPT");
		if ((interestOps & SelectionKey.OP_READ) != 0)
			list.add("OP_READ");
		if ((interestOps & SelectionKey.OP_WRITE) != 0)
			list.add("OP_WRITE");
		return String.join("|", list);
	}

	public void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet) {
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
			}

		}
	}

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e) {
			return "???";
		}
	}

	private void printSelectedKey() {
		if (selectedKeys.isEmpty()) {
			System.out.println("There were not selected keys.");
			return;
		}
		System.out.println("The selected keys are :");
		for (SelectionKey key : selectedKeys) {
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println(
						"\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
			}

		}
	}

	private String possibleActionsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		ArrayList<String> list = new ArrayList<>();
		if (key.isAcceptable())
			list.add("ACCEPT");
		if (key.isReadable())
			list.add("READ");
		if (key.isWritable())
			list.add("WRITE");
		return String.join(" and ", list);
	}
}