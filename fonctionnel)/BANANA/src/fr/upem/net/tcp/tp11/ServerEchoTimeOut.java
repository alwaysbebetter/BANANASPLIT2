package fr.upem.net.tcp.tp11;



import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Set;

public class ServerEchoTimeOut {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	static private final int BUFSIZ = 1024;
	static private final long TIMEOUT = 3000;

	private class Attachement {
		ByteBuffer buff;
		boolean isClosed = false;
		long lastDateOfActivation;

		public Attachement() {
			buff = ByteBuffer.allocate(BUFSIZ);
			lastDateOfActivation = 0;
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

	public ServerEchoTimeOut(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
	}

	public void checkTime() throws IOException {

		long curTime = 0;
		for (SelectionKey key : selector.keys()) {
			curTime = System.currentTimeMillis();

			Attachement theAttachement = (Attachement) key.attachment();

			if (theAttachement != null) { // si c'est null c'eest que c'est le
											// server ou autre et ça a on est
											// pas sensé le fermer

				System.out.println(curTime
						- theAttachement.lastDateOfActivation);
				if ((theAttachement.lastDateOfActivation != 0)
						&& ((curTime - theAttachement.lastDateOfActivation) > TIMEOUT)) {
					System.out.println("coucou");
					key.channel().close();
				} else {

				}
			}
		}

	}



	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		
		while (!Thread.interrupted()) {
			printKeys();
			System.out.println("Starting select");
			selector.select(TIMEOUT);

			System.out.println("Select finished");
			//printSelectedKey();
			checkTime();
			processSelectedKeys();
			selectedKeys.clear();
			
		}
	}

	// pour le time out on ne fait pas de thread.
	// a chaque selecte on regarde qu'elle heur il est et ensuite on netois
	// on remet a jour directement le même dépot conernant le dernier tp
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

	// DEMARQQUER LA DATE A AU VERAIEMENT MOMENT OU IL YA L'ACTIVITE
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

		
			if (theAttachement.buff.position() == 0) {
				client.close();
			}
			
		}
		else {
			theAttachement.lastDateOfActivation = System.currentTimeMillis();
		}
		key.interestOps(theAttachement.getInterest());

	}

	
	private void doWrite(SelectionKey key) throws IOException {
		SocketChannel client = (SocketChannel) key.channel();
		Attachement theAttachement = (Attachement) key.attachment();
		theAttachement.buff.flip();
		
		
		if (0 <= client.write(theAttachement.buff)) {
			// si on arrive ici c'est que il y a bien eu une recepetion.
			theAttachement.lastDateOfActivation = System.currentTimeMillis();
		}
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
		new ServerEchoTimeOut(Integer.parseInt(args[0])).launch();

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
			System.out
					.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet) {
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : "
						+ interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client "
						+ remoteAddressToString(sc) + " : "
						+ interestOpsToString(key));
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
				System.out.println("\tServerSocketChannel can perform : "
						+ possibleActionsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tClient " + remoteAddressToString(sc)
						+ " can perform : " + possibleActionsToString(key));
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