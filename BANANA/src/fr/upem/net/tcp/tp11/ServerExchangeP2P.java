package fr.upem.net.tcp.tp11;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class ServerExchangeP2P {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final HashMap<Id, Attachement> map = new HashMap<>();
	static private final int BUFSIZ = 4;
	
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
		int sizeMessage = 0;
		boolean isClosed = false;
		SelectionKey key;
		StatusExchange status = StatusExchange.BEGIN;
		




		
		
		
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
				interest |= SelectionKey.OP_WRITE;
				System.out.println("OP_WRITE");
			}
			if (!isClosed && in.hasRemaining()) {
				System.out.println("OP_READ");
				interest |= SelectionKey.OP_READ;
			}
	
			return interest;

		}

		public void buildOut() {

			if (in.position() >= 4 && status == StatusExchange.BEGIN ) {
				// getInt la tail
				// System.out.println("in : " + in);
				in.flip();

				sizeMessage = in.getInt();
		
				System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX size :"+sizeMessage);
				// System.out.println("in après : " + in);
				if (sizeMessage <= 0 || sizeMessage >= 1024 ){
					return ; // close
				}
				out = ByteBuffer.allocate(sizeMessage);
				// System.out.println("NBOP 1 : " + nbOp);
				in.compact();
				status = StatusExchange.MIDDLE;
				
				
			}
			if (( in.position() < sizeMessage ) && (status == StatusExchange.MIDDLE)) {
				System.out.println("1111111111111111111111111111");
				if( in.position() < sizeMessage ){
					System.out.println("position :"+in.position());
					System.out.println("222222222222222222222222");
					System.exit(1);
					return ;
				}
				int oldLimit = in.limit();
				in.limit(sizeMessage);
				outn);
				in.limit(oldLimit);
				in.compact();
				status = StatusExchange.END;
			}

		}

	}

	public ServerExchangeP2P(int port) throws IOException {
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
	
			System.out.println("Starting select");
			System.out.println("-------------------A---------------------------");
			selector.select();
			System.out.println("Select finished");
			System.out.println("-------------------B---------------------------");
			processSelectedKeys();
			System.out.println("-------------------C---------------------------");
			selectedKeys.clear();
			System.out.println("-------------------D---------------------------");
		}
	}

	private void processSelectedKeys() throws IOException {
		for (SelectionKey key : selectedKeys) {
			System.out.println("-------------------0---------------------------");
			if (key.isValid() && key.isAcceptable()) {
				System.out.println("-------------------1---------------------------");
				doAccept(key);// on ne catrch pas cette exception parce que si
								// le accept
				// pete c'et que le serveur est mor
			}
			try { // on la catch ici car on arrete pas le serveur pour ça
				
				System.out.println("-------------------2---------------------------");
				if (key.isValid() && key.isWritable()) {
					System.out.println("-------------------3---------------------------");
					doWrite(key);
				}
				if (key.isValid() && key.isReadable()) {
					System.out.println("-------------------4---------------------------");
					doRead(key);
				}
			} catch (IOException e) {
				System.out.println("-------------------5---------------------------");
				;
			}
			System.out.println("-------------------6---------------------------");
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
			/*
			 * Si la position dans le buffer in est 0 c'est necessairement qu'on a tout lu
			 * alors si on a reperer une fermeture de l'autre coté il ne reste plus rien a lirse 
			 * sire ma sop
			 * 
			 */
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
		new ServerExchangeP2P(Integer.parseInt(args[0])).launch();

	}




	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e) {
			return "???";
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