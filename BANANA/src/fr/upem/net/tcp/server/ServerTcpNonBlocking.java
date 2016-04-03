package fr.upem.net.tcp.server;

import java.io.IOException;

import java.io.Writer;
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
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import fr.upem.net.tcp.protocol.Writters;;

public class ServerTcpNonBlocking {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final HashMap<String, Attachement> map = new HashMap<>();
	public static final Charset UTF_8 = Charset.forName("utf-8");
	static private final int BUFSIZ = 10;
	private static final Random rand = new Random();

	/*
	 * ------------------------------- ENUM
	 * -----------------------------------------------------------
	 */

	/* --------------------- STATUS ENUM ------------------- */

	private enum StatusTreatment {

		TYPE_READING, TYPE_KNOWN, CHOOSE_TREATING, READ_LOGIN, END_READING, END_TREATMENT, REFILL, ERROR, DONE

	}

	private enum StatusExchange {
		WAITING_TO_CO_SERV(0), CONNECTED_TO_SERV(1), WAITING_TO_CO_PRV(2), CONNECTED_TO_PRV(
				3);
		private final int value;

		public int getValue() {
			return value;
		}

		private StatusExchange(int value) {
			this.value = value;
		}
	}

	/* --------------------- PACKET TYPE ENUM ------------------- */

	public enum TypePacket {

		// TO ESTABLISH CONNECTION TO TCHAT
		ASC_CO_SERV(0), // Demande de connection au serveux C1 ->S
		ACC_CO_SERV(1), // Acception de connection au serveur S -> C1
		REF_CO_SERV(2), // Refue de connection au serveur S -> C1

		// TO ESTABLISH CONNECTION PRIVATE TO MESSAGE
		ASC_CO_PRV_CS(3), // Demande de connection privé C1 -> S (vers C2)
		ASC_CO_PRV_SC(4), // Demande de connection privé part2 S -> C2 (venant
							// de
		// C1)
		ACC_CO_PRV_CS(5), // Acceptation connection privé C2 -> S (vers C1)
		REF_CO_PRV_CS(6), // Refue de connection privé C2 -> S (vers C1)
		ACC_CO_PRV_SC(7), // Acceptation connection privé parte2 S -> C1 (venant
							// de
		// C2)
		REF_CO_PRV_SC(8), // Refue de connection privé part2 S -> C1 (venant de
							// C2)

		// TO ESTABLISH CONNECTION PRIVATE TO FILE
		ASC_CO_FIL_CC(9), // Demande de connection privé fichier C1 -> S (vers
							// C2)
		ACC_CO_FIL_CC(10), // Acceptation de connection privé pour fichier C2 ->
							// S
		// (vers C1)
		ASC_SEND_FIL_CC(11), // Demande d’envoie de fichié C1 -> C2
		ACC_SEND_FIL_CC(12), // Acceptation de la demande d’envoit de fichier C2
								// -> C1
		REF_SEND_FIL_CC(13), // Refu de la demande d’envoie de fichier

		// TO SEND FILE
		FILE(14),

		// TO SEND MESSAGE
		MESSAGE(15);
		private final int value;

		public int getValue() {
			return value;
		}

		private TypePacket(int value) {
			this.value = value;
		}
	}

	/*
	 * ---------------------------------- ATTACHMENT
	 * ------------------------------------------------
	 */

	private boolean isAUniqLogin(String login) {
		if (map.get(login) == null) {
			return true;
		}
		return false;
	}

	private class Attachement {
		public final static int SRC_DATA = 0, DEST_DATA = 1, DEST_DATA_SRC = 2;
		ByteBuffer in, out;
		String login;
		long id;
		boolean isClosed = false;
		SelectionKey key;
		Queue<DataPacketRead> bbWaitingsToBeUsed;
		StatusExchange statusExchange = StatusExchange.WAITING_TO_CO_SERV;
		StatusTreatment statusTreatment = StatusTreatment.TYPE_READING;
		TypePacket typeLastPacketReceiv;
		Reader readerACC_CO_PRV_CS, readerASC_CO_PRV_CS, readerREF_CO_PRV_CS,
				readerMESSAGE, currentReader;
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

		public int getInterest() throws IOException {
			int interest = 0;
			if (out.position() > 0) {
				interest = interest | SelectionKey.OP_WRITE;
			}
			if (!isClosed && in.hasRemaining()) {
				interest |= SelectionKey.OP_READ;
			}

			return interest;

		}

		public void fillQueue() {
			if ((statusTreatment == StatusTreatment.TYPE_READING)
					&& (in.position() >= 1)) {
				in.flip();
				// get type
				typeLastPacketReceiv = TypePacket.values()[in.get()];
				in.compact();
				// change status
				if (!isAnExpectedTypePacket(typeLastPacketReceiv)) {
					// TODO : NOP/C, aprioris un close mais attendre les
					// vérification
					// du prof, puis il faut bien le faire le clsoe
					// attention
					// car il y a des gestion avec getInterrest et tout
					// TODO : return
				}

				switch (typeLastPacketReceiv) {
				case ASC_CO_SERV:

					// TODO : Check that's was expected according the
					// statusExchage
					// et statusTreatement
					// TODO : find checks

					currentReader = new ReaderString(SRC_DATA);

					break;

				case ASC_CO_PRV_CS:// Code : 3

					// TODO : Check that's was expected according the
					// statusExchage
					// et statusTreatement
					// TODO : find checks
					if (readerASC_CO_PRV_CS == null) {
						readerASC_CO_PRV_CS = new ReaderString(new ReaderLong(
								new ReaderString(SRC_DATA)), DEST_DATA);
					}
					currentReader = readerASC_CO_PRV_CS;
					break;
				case ACC_CO_PRV_CS:// Code : 5
					// TODO : Check that's was expected according the
					// statusExchage
					// et statusTreatement
					// TODO : find checks
					if (readerACC_CO_PRV_CS == null) {
						readerACC_CO_PRV_CS = new ReaderInt(new ReaderString(
								new ReaderLong(new ReaderString(SRC_DATA)),
								DEST_DATA_SRC));
					}
					currentReader = readerACC_CO_PRV_CS;
					break;
				case REF_CO_PRV_CS:// Code : 5

					// TODO : Check that's was expected according the
					// statusExchage
					// et statusTreatement
					// TODO : find checks
					if (readerREF_CO_PRV_CS == null) {
						readerREF_CO_PRV_CS = new ReaderLong(new ReaderString(
								SRC_DATA));
					}
					currentReader = readerREF_CO_PRV_CS;
					break;
				case MESSAGE:// Code :15

					// TODO : Check that's was expected according the
					// statusExchage
					// et statusTreatement
					// TODO : find checks
					if (readerMESSAGE == null) {
						readerMESSAGE = new ReaderString(new ReaderLong(
								new ReaderString(SRC_DATA)), DEST_DATA);// on cousidere le message come un login desinataire pour economiser les change et on peut faire ça car on a la meêm limitation en tail sur le message que sur le login
					}
					currentReader = readerMESSAGE;
					break;

				default: // close
				}
			}
			if (statusTreatment == StatusTreatment.TYPE_KNOWN) {
				switch (currentReader.process(in)) {
				case DONE:
					DataPacketRead dataPacketRead = currentReader.get();
					dataPacketRead.setTypePacket(typeLastPacketReceiv);
					bbWaitingsToBeUsed.add(dataPacketRead);
					statusTreatment = StatusTreatment.END_READING;
					break;
				case ERROR:
					// TODO : close
					break;
				case REFILL:
					return;
				}
			}

		}

		/**
		 * isAnExpectedTypePacket :
		 * 
		 * return if this type packet was expected according the current state
		 * of Exchange ( statusExchange ) return true if it's expected if else
		 * false
		 * 
		 * @param:
		 */
		public boolean isAnExpectedTypePacket(TypePacket typePacket) {// NOTE it
																		// controle
																		// that
																		// automate
																		// of
																		// state
																		// is
																		// good
			switch (statusExchange) {
			case WAITING_TO_CO_SERV:
				if (typePacket.equals(TypePacket.ASC_CO_SERV))
					return true;
				return false;
			case CONNECTED_TO_SERV:
				if (typePacket.equals(TypePacket.ASC_CO_PRV_CS)
						|| typePacket.equals(TypePacket.MESSAGE))
					return true;
				return false;
			case WAITING_TO_CO_PRV:
				if (typePacket.equals(TypePacket.REF_CO_PRV_CS)
						|| typePacket.equals(TypePacket.ACC_CO_PRV_CS)
						|| typePacket.equals(TypePacket.MESSAGE))
					return true;
				return false;
			case CONNECTED_TO_PRV:
				if (typePacket.equals(TypePacket.MESSAGE))
					return true;
				return false;
			}
			return false;
		}

		public void realBuildOut(TypePacket typePacketToSend) {
			// TODO: check if byte to in will not be a pb
			out.put((byte) typePacketToSend.getValue());
		}


		private String readString(ByteBuffer bb) {
			int size = bb.getInt();
			int oldLimit = bb.limit();
			bb.limit(size);
			String theString = UTF_8.decode(bb).toString();
			bb.limit(oldLimit);
			return theString;
		}

		public void dispatch() {
			if (bbWaitingsToBeUsed.isEmpty())
				return;
			DataPacketRead data = bbWaitingsToBeUsed.poll();
			TypePacket typePacket = data.getTypePacket();
			if (!isAnExpectedTypePacket(typePacket)) {/* close */
			}
			switch (typePacket) {
			case ASC_CO_SERV:
				// je test la car ça pourrait êre faut au moment ou on le
				// recupere de lafile
				login = data.getLoginSrc();

				if (!isAUniqLogin(login)) {
					// TODO : if false login we refused connexion ?
					// appeler la fonction qui va remplir le out avec le
					// packet de refu ( et fermer la connection ? )
					realBuildOut(TypePacket.REF_CO_SERV);
					return;
				}
				// TODO: do we have to manage the unicity with a comparason

				id = rand.nextLong();

				// TODO ; add the client, here ?
				map.put(login, this);

				// TODO : Appeler la fonction qui va remplir le out avec
				// le paquet d'acceptation.
				realBuildOut(TypePacket.ACC_CO_SERV);

				// change status of exchange
				statusExchange = StatusExchange.CONNECTED_TO_SERV;
				// TODO: Verifier si c'est pas la qu'on change l'état
				// WRITE ou READ ( je pense pas non )

				statusTreatment = StatusTreatment.END_TREATMENT;

				break;
			case ASC_CO_PRV_CS:// code 3
				// if client doesn't existe
				// if it's the same client

				if ((!data.getLoginSrc().equals(login)) || (id != data.getId())) {
					// si il s'agit d'une usurpation d'identité on ferme la
					// connection
					// TODO: close
				}

				String loginDest = data.getLoginDst();
				if (map.get(loginDest) == null) {
					// TODO: ne rien faire car il se peut que le destinataire ce
					// soit déconnecté,
					// on aura alors une gestion du time out pour l'attente de
					// l'aquitemetn deml par du client
					// on pourarait faire en sort que ce soit le serveur qui
					// dans ce cas renvoit uenun paquet
					// pour dirt que l'utilisateur n'est plus disponible
					// ouaalors simplement pour marqué
					// le refu mais depuis le serveur,
				}

				// WRITTER
				// realBuildOut(TypePacket.ACC_CO_SERV);

				statusExchange = StatusExchange.WAITING_TO_CO_PRV;

				statusTreatment = StatusTreatment.END_TREATMENT;

				break;

			case ACC_CO_PRV_CS:
				
				if ((!data.getLoginSrc().equals(login)) || (id != data.getId())) {
					// si il s'agit d'une usurpation d'identité on ferme la
					// connection
					// TODO: close
				}
				
				// attention c'est l'adresse privé c'est pour ça que debase le serveur ne la connait aps et qu'il la communique.
				// TODO/ dans le rapport il faudra bien mettre en avant ce que gere le serveur, notemment il empeche l'usurapation

				Writters.aquitPrivateConnection(TypePacket.ACC_CO_PRV_SC,data.getAdrSrc(),data.getPortSrc(),out);
				
				// ON A CONNAISSANCE DU LOGIN ne confond pas avec la trame qu'on compose !!!!!

				//--------------------------------------------------
				//|int| int                   | String       | int       |
				//|7  | taille address | address    | port     |
				//---------------------------------------------------
					
				
				statusExchange = StatusExchange.CONNECTED_TO_PRV;
				// TODO : on doit accede a l'autre client poru envoyer la trame sur ça socket et aussi pour changer son statu

				statusTreatment = StatusTreatment.END_TREATMENT;
				break;
			case REF_CO_PRV_CS:
				if ((!data.getLoginSrc().equals(login)) || (id != data.getId())) {
					// si il s'agit d'une usurpation d'identité on ferme la
					// connection
					// TODO: close
				}
				
				/*
				 
-----------------------------------------
|int| int                      | String      | id
| 6 | taille pseudo c1 |pseudo c1| long
----------------------------------------- 
 
				 */
				realBuildOut(TypePacket.REF_CO_PRV_SC);

				statusExchange = StatusExchange.CONNECTED_TO_SERV;

				statusTreatment = StatusTreatment.END_TREATMENT;
				break;
			case MESSAGE:
				/*
				 * 
-------------------------------------------------------------------------------------------
|int   | int                   | String       | long | int                      | String     |
|15    | taille pseudo         |pseudo        |  id  | taille message           | Message|
-------------------------------------------------------------------------------------------
				 * 
				 * 
				 * 
				 * 
				 */
				if ((!data.getLoginSrc().equals(login)) || (id != data.getId())) {
					// si il s'agit d'une usurpation d'identité on ferme la
					// connection
					// TODO: close
				}
				//Writters.aquitPrivateConnection(TypePacket.MESSAGE, loginDest, port, out);
				//Writters.sendMessage(sc, src, data.getLoginDst()/*size message*/);
			
				

				break;

			}

		}

		// We must do those check of status to the treatment because because in
		// tcp non blockin, the reading can be not finished in one time
		public void buildOut() {
			fillQueue();
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

		if (theAttachement.statusTreatment == StatusTreatment.END_READING) {
			// Dispatch
			theAttachement.out.flip();

			client.write(theAttachement.out);

			theAttachement.out.compact();

			if (theAttachement.isClosed) {
				client.close();
				theAttachement.isClosed = true;
			}

			// theAttachement.status = StatusTreatment.BEGIN;
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