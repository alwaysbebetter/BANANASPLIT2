package fr.upem.net.tcp.tp11;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class ServerLongSum {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final HashMap<Id, Attachement> map = new HashMap<>();
	static private final int BUFSIZ = 10;
	
	private class Id {
		long id ;
		String login ;
	}

	private enum StatusExchange {
		BEGIN,MIDDLE,END
	}
	
	private class Attachement {
		ByteBuffer in;
		ByteBuffer out;
		int nbOp = 0;
		long sum = 0;
		boolean isClosed = false;
		SelectionKey key;
		StatusExchange status = StatusExchange.BEGIN;
		




		
		class StatusSession {
			
			
		}
		
		
		
		
		public Attachement(SelectionKey key) {
			in = ByteBuffer.allocate(BUFSIZ);
			out = ByteBuffer.allocate(BUFSIZ);
			this.key = key;
		}

		public int getInterest() throws IOException { // il faut le faire aprés
														// avoir unappeler
			// process ET ça il faut le préciser dans le
			// protocole.
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

			if (in.position() >= 4 && status == StatusExchange.BEGIN ) {
				// getInt la tail
				// System.out.println("in : " + in);
				in.flip();
				nbOp = in.getInt();
				// System.out.println("in après : " + in);
				out = ByteBuffer.allocate(Long.BYTES * nbOp);
				// System.out.println("NBOP 1 : " + nbOp);
				sum = 0;
				status = StatusExchange.MIDDLE;
				in.compact();
				return;

			}
			/*
			 * if (!first) {
			 * 
			 * while ((nbOp > 0) && (in.position() > 2)) { sum += in.getLong();
			 * nbOp++; }
			 * 
			 * in.compact(); out.putLong(sum); // reinitialize sum sum = 0;
			 * 
			 * }
			 */
			System.out.println("nbOP15 : " + nbOp);
			if ((status == StatusExchange.MIDDLE) && (nbOp > 0)) {

				if (in.position() >= Long.BYTES) {
					in.flip();
					sum += in.getLong();
					nbOp--;
					in.compact();
					System.out.println("statut  :" + status);
					System.out.println("nbop :" + nbOp);
					if (nbOp == 0) {
						out.putLong(sum);
						System.out.println("Sum :" + sum);
						status = StatusExchange.END;
					}

				}

			}
			System.out.println("nbOP16 : " + nbOp);
		}

	}

	public ServerLongSum(int port) throws IOException {
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
		sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE,
				new Attachement(key));

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
		int interrest;
		if ((interrest = theAttachement.getInterest()) != 0) {
			key.interestOps(interrest);
		}

	}

	private void doWrite(SelectionKey key) throws IOException {
		SocketChannel client = (SocketChannel) key.channel();
		Attachement theAttachement = (Attachement) key.attachment();

		if (theAttachement.status == StatusExchange.END ) {

			theAttachement.out.flip();

			client.write(theAttachement.out);

			theAttachement.out.compact();

			if (theAttachement.isClosed) {
				client.close();
				theAttachement.isClosed = true;
			}

			theAttachement.status = StatusExchange.BEGIN ;
		}

		key.interestOps(theAttachement.getInterest());

	}

	public static void main(String[] args) throws NumberFormatException,
			IOException {
		new ServerLongSum(Integer.parseInt(args[0])).launch();

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