package fr.upem.net.tcp.server;

import java.awt.Window.Type;
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
import java.util.Random;
import java.util.Set;

public class ServerTcpNonBlocking {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final HashMap<Id, Attachement> map = new HashMap<>();
	static private final int BUFSIZ = 10;
	private static final Charset UTF_8 = Charset.forName("utf-8");
	private static final Random rand = new Random();
	private class Id {
		long id;
		String login;
		public Id( long id , String login ){
			this.id = id ;
			this.login = login ;
		}
	}

	private enum StatusTreatment {
		BEGIN, MIDDLE, END, // TODO : delete those three value when i will decide
		TYPE_READING, 
		CHOOSE_TREATING,
		READ_LOGIN,
		END_READING,
		END_TREATMENT

	}

	private enum TypePacket {

		// TO ESTABLISH CONNECTION TO TCHAT
		ASC_CO_SERV(0),// Demande de connection au serveux C1 ->S
		ACC_CO_SERV(1), // Acception de connection au serveur S -> C1
		REF_CO_SERV(2), // Refue de connection au serveur S -> C1

		// TO ESTABLISH CONNECTION PRIVATE TO MESSAGE
		ASC_CO_PRV_CS(3), // Demande de connection privé C1 -> S (vers C2)
		ASC_CO_PRV_SC(4), // Demande de connection privé part2 S -> C2 (venant de
		// C1)
		ACC_CO_PRV_CS(5), // Acceptation connection privé C2 -> S (vers C1)
		REF_CO_PRV_CS(6), // Refue de connection privé C2 -> S (vers C1)
		ACC_CO_PRV_SC(7), // Acceptation connection privé parte2 S -> C1 (venant de
		// C2)
		REF_CO_PRV_SC(8), // Refue de connection privé part2 S -> C1 (venant de C2)

		// TO ESTABLISH CONNECTION PRIVATE TO FILE
		ASC_CO_FIL_CC(9), // Demande de connection privé fichier C1 -> S (vers C2)
		ACC_CO_FIL_CC(10), // Acceptation de connection privé pour fichier C2 -> S
		// (vers C1)
		ASC_SEND_FIL_CC(11), // Demande d’envoie de fichié C1 -> C2
		ACC_SEND_FIL_CC(12), // Acceptation de la demande d’envoit de fichier C2 -> C1
		REF_SEND_FIL_CC(13), // Refu de la demande d’envoie de fichier

		// TO SEND FILE
		FILE(14),

		// TO SEND MESSAGE
		MESSAGE(15);
		private final int value ;
		public int getValue ( ){
			return value ;
		}
		private TypePacket( int value ) {
			this.value = value;
		}
	}

	private enum StatusExchange {
		WAITING_TO_CO_SERV(0),CONNECTED_TO_SERV(1),WAITING_TO_CO_PRV(2),CONNECTED_TO_PRV(3);
		private final int value ;
		public int getValue (){
			return value ;
		}
		private StatusExchange( int value ) {
			this.value = value;
		}
	}

	private boolean isAUniqLogin(String login) {
		for (Id id : map.keySet()) {
			if (id.login.equals(login)) {
				return false;
			}
		}
		return true;
	}
	
	

	private class Attachement {
		ByteBuffer in;
		ByteBuffer out;
		int nbOp = 0;
		long sum = 0;
		boolean isClosed = false;
		SelectionKey key;
		StatusTreatment statusTreatment = StatusTreatment.BEGIN;
		StatusExchange statusExchange = StatusExchange.WAITING_TO_CO_SERV ;
		TypePacket typeLastPacketReceiv;
		int sizeLogin = -1;

		public Attachement(SelectionKey key) {
			in = ByteBuffer.allocate(BUFSIZ);
			out = ByteBuffer.allocate(BUFSIZ);
			this.key = key;
		}

		private boolean isValideTypePacket(byte typePacket) {
			if (typePacket < 0 || typePacket > 15)
				return false;
			return true;
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
		
		public void realBuildOut ( TypePacket typePacketToSend  ){
			out.put((byte)typePacketToSend.getValue());// TODO: check if byte to in will not be a pb
		}

		/**
		 * treatmentPacket :
		 * 
		 * will only treat received packet
		 * 
		 */
		private void treatmentPacket() {
			// TODO: logger pour afficher le buffer pour verifier
			switch (typeLastPacketReceiv) {

			case ASC_CO_SERV:

				// TODO : Check that's was expected according the statusExchage
				// et statusTreatement

				// TODO : read login
				switch (statusTreatment) {
				case CHOOSE_TREATING:
					if (in.position() >= 4) {
						in.flip();
						sizeLogin = in.getInt();
						in.compact();
						statusTreatment = StatusTreatment.READ_LOGIN;
					}
					break;
				case READ_LOGIN:
					
					if ( (in.position() >= sizeLogin) && (sizeLogin != -1) ) {
						in.flip();
						String login = UTF_8.decode(in).toString();
						in.compact();
						// check unicity of login
						if( ! isAUniqLogin(login) ){
							// TODO : if false login we refused connexion ? 
							// appeler la fonction qui va remplir le out avec le packet de refu ( et fermer la connection ? )
							realBuildOut(TypePacket.REF_CO_SERV);
							return ;
							
						}
						
						
						// generate uniq long and create the id
						long id = rand.nextLong();// TODO: do we have to manage the unicity with a comparason
						
						// TODO ; add the client, here ?
						map.put(new Id(id,login), this);
						
						
						// TODO : Appeler la fonction qui va remplir le out avec le paquet d'acceptation.
						realBuildOut(TypePacket.ACC_CO_SERV);
						
						//change status of treatment
						statusTreatment = StatusTreatment.END_READING ;// TODO : delete, but check if we can before
						
						// change status of exchange
						statusExchange = StatusExchange.CONNECTED_TO_SERV ;
						// TODO: Verifier si c'est pas la qu'on change l'état WRITE ou READ ( je pense pas non )
						statusTreatment = StatusTreatment.END_TREATMENT;
					}
					// TODO : find checks

					/*
					 * TO THE SENDING case ACC_CO_SERV:
					 * 
					 * // TODO : Check that's was expected according the
					 * statusExchage et statusTreatement // TODO : find checks
					 * 
					 * break;
					 */
					/*
					 * TO THE SENDING case REF_CO_SERV: // TODO : Check that's was
					 * expected according the statusExchage et statusTreatement //
					 * TODO : find checks break;
					 */
					break;
					
					default ://TODO : bad case, reject
				}

				return;

				
			case ASC_CO_PRV_CS:
				// TODO : Check that's was expected according the statusExchage
				// et statusTreatement
				// TODO : find checks
				break;
			/*
			 * TO THE SENDING case ASC_CO_PRV_SC: // TODO : Check that's was
			 * expected according the statusExchage et statusTreatement // TODO
			 * : find checks break;
			 */
			case ACC_CO_PRV_CS:
				// TODO : Check that's was expected according the statusExchage
				// et statusTreatement
				// TODO : find checks
				break;
			case REF_CO_PRV_CS:
				// TODO : Check that's was expected according the statusExchage
				// et statusTreatement
				// TODO : find checks
				break;
			/*
			 * TO THE SENDING case ACC_CO_PRV_SC: // TODO : Check that's was
			 * expected according the statusExchage et statusTreatement // TODO
			 * : find checks break; case REF_CO_PRV_SC: // TODO : Check that's
			 * was expected according the statusExchage et statusTreatement //
			 * TODO : find checks break;
			 */
			case ASC_CO_FIL_CC:
				// TODO : Check that's was expected according the statusExchage
				// et statusTreatement
				// TODO : find checks
				break;
			case ACC_CO_FIL_CC:
				// TODO : Check that's was expected according the statusExchage
				// et statusTreatement
				// TODO : find checks
				break;
			/*
			 * ONLY TO CLIENTS case ASC_CO_FIL_CC: // TODO : Check that's was
			 * expected according the statusExchage et statusTreatement // TODO
			 * : find checks break; case ACC_CO_FIL_CC: // TODO : Check that's
			 * was expected according the statusExchage et statusTreatement //
			 * TODO : find checks break; case REF_CO_FIL_CC: // TODO : Check
			 * that's was expected according the statusExchage et
			 * statusTreatement // TODO : find checks break; case FILE: // TODO
			 * : Check that's was expected according the statusExchage et
			 * statusTreatement // TODO : find checks break;
			 */
			case MESSAGE:
				// TODO : Check that's was expected according the statusExchage
				// et statusTreatement
				// TODO : find checks
				break;

			default:
				// TODO : client will be denied without waring him
				// TODO : Check if it is always a problem

			}
		}

		// We must do those check of status to the treatment because because in
		// tcp non blockin, the reading can be not finished in one time
		public void buildOut2() {
			if ( /* conditions && */(in.position() >= Byte.BYTES)
					&& (statusTreatment == StatusTreatment.TYPE_READING)) {
				in.flip();

				byte typePacket = in.get();

				if (!isValideTypePacket(typePacket)) {
					// TODO : REJECT
				}

				typeLastPacketReceiv = TypePacket.values()[in.get()];// TODO:
																		// HECK
																		// IF
																		// THAT
																		// WILL
																		// NO
																		// IMPLIES
																		// PROBLEM

				in.compact();

				statusTreatment = StatusTreatment.CHOOSE_TREATING;

				return;
			}

			if ( /* conditions && */statusTreatment == StatusTreatment.CHOOSE_TREATING) {
				treatmentPacket();
			}
		}

		public void buildOut() {

			if (in.position() >= 4 && statusTreatment == StatusTreatment.BEGIN) {
				// getInt la tail
				// System.out.println("in : " + in);
				in.flip();
				nbOp = in.getInt();
				// System.out.println("in après : " + in);
				out = ByteBuffer.allocate(Long.BYTES * nbOp);
				// System.out.println("NBOP 1 : " + nbOp);
				sum = 0;
				statusTreatment = StatusTreatment.MIDDLE;
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
			if ((statusTreatment == StatusTreatment.MIDDLE) && (nbOp > 0)) {

				if (in.position() >= Long.BYTES) {
					in.flip();
					sum += in.getLong();
					nbOp--;
					in.compact();
					System.out.println("statut  :" + statusTreatment);
					System.out.println("nbop :" + nbOp);
					if (nbOp == 0) {
						out.putLong(sum);
						System.out.println("Sum :" + sum);
						//status = StatusTreatment.END;
					}

				}

			}
			System.out.println("nbOP16 : " + nbOp);
		}

	}

	public ServerTcpNonBlocking(int port) throws IOException {
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

		if (theAttachement.statusTreatment == StatusTreatment.END_TREATMENT) {

			theAttachement.out.flip();

			client.write(theAttachement.out);

			theAttachement.out.compact();

			if (theAttachement.isClosed) {
				client.close();
				theAttachement.isClosed = true;
			}

			//theAttachement.status = StatusTreatment.BEGIN;
		}

		key.interestOps(theAttachement.getInterest());

	}

	public static void main(String[] args) throws NumberFormatException,
			IOException {
		new ServerTcpNonBlocking(Integer.parseInt(args[0])).launch();

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