package fr.upem.net.tcp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import fr.upem.net.logger.Loggers;
import fr.upem.net.tcp.server.Reader.StatusProcessing;

public class ServerMultiChatTCPNonBlockingWithQueueGoToMatou3 {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final ConcurrentHashMap<String, Attachement> map = new ConcurrentHashMap<>();
	private int co = 0;
	private int debug = 0;
	static private final int BUFSIZ = 200;
	public static final Charset UTF_8 = Charset.forName("utf-8");
	public final static int SRC_DATA = 0, DEST_DATA = 1, SRC_DATA_ADR = 2;
	private Random rand = new Random();

	private enum StatusTreatment {

		TYPE_READING, TYPE_KNOWN, CHOOSE_TREATING, READ_LOGIN, END_READING, REFILL, ERROR, DONE, READER_KNOWN, DATA_PACKET_KNOWN

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
		MESSAGE(15),

		INITIAL_TYPE(16);
		private final byte value;

		public byte getValue() {
			return value;
		}

		private TypePacket(int value) {
			this.value = (byte) value;
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

		ByteBuffer in, out;
		boolean isClosed = false;
		String login;
		LinkedList<ByteBuffer> queue = new LinkedList<>();
		public DataPacketRead dataPacketRead;
		StatusExchange statusExchange = StatusExchange.WAITING_TO_CO_SERV;
		StatusTreatment statusTreatment = StatusTreatment.TYPE_READING;
		TypePacket typeLastPacketReceiv = TypePacket.INITIAL_TYPE;
		Reader readerACC_CO_PRV_CS, readerASC_CO_PRV_CS, readerREF_CO_PRV_CS,
				readerASC_CO_SERV, readerMESSAGE, currentReader;
		long id;
		SocketChannel sc;
		String loginDest;

		public Attachement(SocketChannel sc) {
			
			this.sc = sc;
			in = ByteBuffer.allocate(BUFSIZ * 4);
			out = ByteBuffer.allocate(BUFSIZ * 4);
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
						|| typePacket.equals(TypePacket.REF_CO_PRV_CS)
						|| typePacket.equals(TypePacket.ACC_CO_PRV_CS)
						|| typePacket.equals(TypePacket.MESSAGE))
					return true;
				return false;
			case WAITING_TO_CO_PRV:
				if (typePacket.equals(TypePacket.MESSAGE))
					//	|| typePacket.equals(TypePacket.ACC_CO_PRV_CS)
						 
					return true;
				return false;
			case CONNECTED_TO_PRV:
				if (typePacket.equals(TypePacket.MESSAGE))
					return true;
				return false;
			}
			return false;
		}

		public int getInterest() {
			int interest = 0;// initialize

			if (out.position() > 0) {
				interest = interest | SelectionKey.OP_WRITE;
			}
			if (!isClosed) {
				interest |= SelectionKey.OP_READ;
			}

			return interest;

		}

		public void silentlyClose(SocketChannel sc) {
			if (sc != null) {
				try {

					sc.close();

				} catch (IOException e) {
					// Ignor
				}
			}
		}

		public void CloseAnRejectClient(String login) {
			silentlyClose(map.get(login).sc);
			// TODO : desalouer
			map.remove(login);
		}

		private boolean isValideTypePacket(byte typePacket) {
			if (typePacket < 0 || typePacket > 15)
				return false;
			return true;
		}

		private void readType() {// CHECKED
			System.out.println("coco");
			if ((statusTreatment == StatusTreatment.TYPE_READING)
					&& (in.position() >= 1)) {
				System.out.println("coco2");
				// System.out.print("Received format packet:");Loggers.test(in);//TODO
				// : displaying to debbug, after remove it
				System.out.println("statusTreatement : " + statusTreatment);// TODO
																			// :
																			// displaying
																			// to
																			// debbug,
																			// after
																			// remove
																			// it
				in.flip();
				// get type
				typeLastPacketReceiv = TypePacket.values()[in.get()];

				in.compact();
				// change status
				if (!isAnExpectedTypePacket(typeLastPacketReceiv)) {
					System.out
							.println("UNEXCPECTED PACKET -> close and remove");// TODO
																				// :
																				// displaying
																				// to
																				// debbug,
																				// after
																				// remove
																				// it
					CloseAnRejectClient(login);
				}

				System.out.println("readType() -> " + typeLastPacketReceiv);// TODO
																			// :
																			// displaying
																			// to
																			// debbug,
																			// after
																			// remove
																			// it
				statusTreatment = StatusTreatment.TYPE_KNOWN;
				System.out.println("statusTreatement : " + statusTreatment);// TODO
																			// :
																			// displaying
																			// to
																			// debbug,
																			// after
																			// remove
																			// it
			}

		}

		// CHECKED but need to be finsh ( default with close
		// socket and remove client ( make a method closing socket and deleteing
		// client )
		// TO CHECK the updating of reader !!
		public void findReader() {

			if (statusTreatment == StatusTreatment.TYPE_KNOWN) {
				if (dataPacketRead != null) {
					// dataPacketRead.reset();// on ricte les donné avant chaque
					// nouvelle lecture
					dataPacketRead.reset();
				}
				System.out.println("findReader -> reader"
						+ typeLastPacketReceiv);// TODO
				// :
				// displaying
				// to
				// debbug,
				// after
				// remove
				// it
				switch (typeLastPacketReceiv) {
				case ASC_CO_SERV:
					if (readerASC_CO_SERV == null) {
						System.out.println("ALLOCATION "+Thread.currentThread().getStackTrace()[1].getLineNumber());
						readerASC_CO_SERV = new ReaderString(SRC_DATA,
								typeLastPacketReceiv);
					}
					currentReader = readerASC_CO_SERV;

					break;

				case ASC_CO_PRV_CS:// Code : 3

					if (readerASC_CO_PRV_CS == null) {
						System.out.println("ALLOCATION "+Thread.currentThread().getStackTrace()[1].getLineNumber());
						readerASC_CO_PRV_CS = new ReaderString(
								new ReaderLong(new ReaderString(SRC_DATA,
										typeLastPacketReceiv)), DEST_DATA);
					}
					currentReader = readerASC_CO_PRV_CS;
					break;
				case ACC_CO_PRV_CS:// Code : 5

					if (readerACC_CO_PRV_CS == null) {
						System.out.println("ALLOCATION "+Thread.currentThread().getStackTrace()[1].getLineNumber());
						readerACC_CO_PRV_CS = new ReaderInt(
								new ReaderString(new ReaderLong(
										new ReaderString(DEST_DATA,
												typeLastPacketReceiv))
										, SRC_DATA_ADR));
					}
					currentReader = readerACC_CO_PRV_CS;
					break;
				case REF_CO_PRV_CS:// Code : 6

					if (readerREF_CO_PRV_CS == null) {
						System.out.println("ALLOCATION "+Thread.currentThread().getStackTrace()[1].getLineNumber());
						readerREF_CO_PRV_CS = new ReaderLong(new ReaderString(
								DEST_DATA, typeLastPacketReceiv));
					}
					currentReader = readerREF_CO_PRV_CS;
					break;
				case MESSAGE:// Code :15

					if (readerMESSAGE == null) {
						System.out.println("ALLOCATION "+Thread.currentThread().getStackTrace()[1].getLineNumber());
						readerMESSAGE = new ReaderString(
								new ReaderLong(new ReaderString(SRC_DATA,
										typeLastPacketReceiv)), DEST_DATA);
					}
					currentReader = readerMESSAGE;
					break;

				default: // close
					System.out.println("UNKNOWN PACKET -> close and remove!!");// TODO
																				// :
																				// displaying
																				// to
																				// debbug,
																				// after
																				// remove
																				// it);
					CloseAnRejectClient(login);
				}
				statusTreatment = StatusTreatment.READER_KNOWN;
				System.out.println("statusTreatement : " + statusTreatment);// TODO
																			// :
																			// displaying
																			// to
																			// debbug,
																			// after
																			// remove
																			// it
			}

		}

		public StatusProcessing applyReader() {

			if (statusTreatment == StatusTreatment.READER_KNOWN) {

				switch (currentReader.process(in)) {
				case DONE:// TRAITEMENT

					System.out.println("applyReader -> DONE");// TODO :
																// displaying to
																// debbug, after
																// remove it
					statusTreatment = StatusTreatment.DATA_PACKET_KNOWN;

					// reset Datzpz
					// dataPacketRead.setTypePacket(typeLastPacketReceiv);
					return StatusProcessing.DONE;
				case ERROR:
					System.out.println("applyReader -> ERROR");// TODO :
																// displaying to
																// debbug, after
																// remove it
					// TODO : close
					CloseAnRejectClient(login);
					return StatusProcessing.ERROR;
				case REFILL:
					System.out.println("applyReader -> REFILL");// TODO :
																// displaying to
																// debbug, after
																// remove it
					return StatusProcessing.REFILL;
				}
			}
			return StatusProcessing.ERROR;//TODO: find other solution
		}

		private void publish(SelectionKey key)
				throws IOException {
			for (SelectionKey key2 : selector.keys()) {
				if (key2.isValid() && (key2.channel() instanceof SocketChannel)
						&& (!key2.equals(key))) {
					out.flip();
					SocketChannel sch = (SocketChannel) key2.channel();
					sch.write(out);

				}

			}
		}
		
		public void writeString(ByteBuffer bb, String s) {
			ByteBuffer tmp = UTF_8.encode(s);
			bb.putInt(tmp.remaining()).put(tmp);
		}

		public void writePacketToSend(DataPacketRead data,
				TypePacket typePacketToSend, ByteBuffer bb) {
			System.out.println("TYPE " + typePacketToSend.getValue());
			bb.put((byte) typePacketToSend.getValue());
			switch (typePacketToSend) {
			case ACC_CO_SERV:
				// not here
				bb.putLong(id = rand.nextLong());
				break;
			case REF_CO_SERV:
				// Do nothing execepte initialize e close ( without remove )
				break;
			case ASC_CO_PRV_SC:
				writeString(bb, data.getLoginSrc());
				break;

			case REF_CO_PRV_SC:
				System.out.println("write in bb REF_CO_PRV_SC");
				// Do nothing
				break;
			case ACC_CO_PRV_SC:
				writeString(bb, data.getAdrSrc());
				bb.putInt(data.getPortSrc());
				break;
			case MESSAGE:

				writeString(bb, data.getLoginSrc());
				// login dst is here the message
				writeString(bb, data.getLoginDst());

			}
			System.out.println("SERVER SEND :");
			Loggers.test(bb);

		}
		
		private void doRead(SelectionKey key) throws IOException {
		

			SocketChannel client = sc ;

			// le problem c'est que el read renoie -1 et que la position est a 0

			if (-1 == client.read(in)) {

				isClosed = true;
				if (in.position() == 0) {

					client.close();
				}

			}
			System.out.println("INTEREST_OPS :" + getInterest());
			readType();
			findReader();
			if( StatusProcessing.ERROR == applyReader()){
				return ;
			}
			treatData();

			key.interestOps(getInterest());
		}
		
		private void doWrite(SelectionKey key) throws IOException {

			Attachement at;

			// faire le techeck sur la taille avant et il fatu faire en sorte que la
			// taille n'excede jamasi celel du buffer qu'on a allouer comme ça pas
			// besoin de reallouer.

			switch (typeLastPacketReceiv) {
			case ASC_CO_SERV:

				out.flip();
				sc.write(out);
				out.compact();

				break;
			case ACC_CO_PRV_CS:
			case REF_CO_PRV_CS:
				System.out.println("réponse a REF_CO_PRV_CS");
				at = map.get(dataPacketRead.getLoginDst());
				out.flip();
				at.sc.write(out);
				out.compact();

				System.out.println("remaaaiinning :"
						+ out.remaining());
				break;
			case ASC_CO_PRV_CS:

				at = map.get(dataPacketRead.getLoginDst());
				out.flip();
				at.sc.write(out);
				out.compact();

				System.out.println("remaaaiinning :"
						+ out.remaining());

				break;
			case MESSAGE:
				if (map.size() > 1) {
					publish(key);
				}

				// synthetethetic clear
				out.clear();
				out.position(out.remaining());
				out.compact();

				System.out.println("remaaaiinning :"
						+ out.remaining());
				break;

			}
			System.out.println("raaa");

			if (isClosed) {
				sc.close();
				isClosed = true;
			}

			key.interestOps(getInterest());
		}


		public void treatData() {

			if (statusTreatment == StatusTreatment.DATA_PACKET_KNOWN) {
				System.out.println("statusTreatment -> " + statusTreatment);
				dataPacketRead = currentReader.get();
				System.out.println("Packet to treat :" + dataPacketRead);
				/*
				 * if (bbWaitingsToBeUsed.isEmpty()) return; DataPacketRead data
				 * = bbWaitingsToBeUsed.poll();
				 */

				TypePacket theTypePacket = TypePacket.values()[dataPacketRead
						.getTypePacket().getValue()];
				if (!isAnExpectedTypePacket(theTypePacket)) {/* close */
					System.out.println("Is unexpectedTypePacket !!");// TODO :
																		// delete
					CloseAnRejectClient(login);//TODO: ET METTRE A NUL L LA VARIABLE CLIENT POUR QU'ELLE SOIT PRISE PAR LE GARBAGE COLLECTORS
				}
				System.out.println("SERVER WILL TREAT :" + theTypePacket);
				switch (theTypePacket) {
				case ASC_CO_SERV:
					// je test la car ça pourrait êre faut au moment ou on le
					// recupere de lafile
					login = dataPacketRead.getLoginSrc();
					if (!isAUniqLogin(login)) {
						// TODO : if false login we refused connexion ?
						// appeler la fonction qui va remplir le out avec le
						// packet de refu ( et fermer la connection ? )
						writePacketToSend(dataPacketRead,
								TypePacket.REF_CO_SERV, out);
						System.out.println("IS NOT UNIQUE LOGIN");// TODO :
						silentlyClose(sc);
						return;// and close se socket
					}
					// TODO: do we have to manage the unicity with a comparason

					id = rand.nextLong();

					// TODO ; add the client, here ?
					map.put(login, this);

					// TODO : Appeler la fonction qui va remplir le out avec
					// le paquet d'acceptation.

					writePacketToSend(dataPacketRead, TypePacket.ACC_CO_SERV,
							out);

					// change status of exchange

					statusExchange = StatusExchange.CONNECTED_TO_SERV;
					// TODO: Verifier si c'est pas la qu'on change l'état
					// WRITE ou READ ( je pense pas non )

					statusTreatment = StatusTreatment.TYPE_READING;

					break;
				case ASC_CO_PRV_CS:// code 3
					// if client doesn't existe
					// if it's the same client

					if ((!dataPacketRead.getLoginSrc().equals(login))
							|| (id != dataPacketRead.getId())) {
						// si il s'agit d'une usurpation d'identité on ferme la
						// connection
						// TODO: close
						CloseAnRejectClient(login);
					}

					String loginDest = dataPacketRead.getLoginDst();
					if (map.get(loginDest) == null) {
						// TODO: ne rien faire car il se peut que le
						// destinataire ce
						// soit déconnecté,
						// on aura alors une gestion du time out pour l'attente
						// de
						// l'aquitemetn deml par du client
						// on pourarait faire en sort que ce soit le serveur qui
						// dans ce cas renvoit uenun paquet
						// pour dirt que l'utilisateur n'est plus disponible
						// ouaalors simplement pour marqué
						// le refu mais depuis le serveur,
						System.out.println("LOGIN DOESN'T EXIST ");// TODO :
																	// delete
						// TODO : envoyer la trame de notification de reffu ( ce couop, si, depuis le server )
					}

					// WRITTER
					// realBuildOut(TypePacket.ACC_CO_SERV);
					writePacketToSend(dataPacketRead, TypePacket.ASC_CO_PRV_SC,
							out);
				//	statusExchange = StatusExchange.WAITING_TO_CO_PRV;

					statusTreatment = StatusTreatment.TYPE_READING;

					break;

				case ACC_CO_PRV_CS:

					if (/*(!dataPacketRead.getLoginSrc().equals(login))
							||*/ (id != dataPacketRead.getId())) {
						// si il s'agit d'une usurpation d'identité on ferme la
						// connection
						// TODO: close
						CloseAnRejectClient(login);
					}

					// attention c'est l'adresse privé c'est pour ça que debase
					// le serveur ne la connait aps et qu'il la communique.
					// TODO/ dans le rapport il faudra bien mettre en avant ce
					// que gere le serveur, notemment il empeche l'usurapation
					writePacketToSend(dataPacketRead, TypePacket.ACC_CO_PRV_SC,
							out);

					// ON A CONNAISSANCE DU LOGIN ne confond pas avec la trame
					// qu'on compose !!!!!

					// --------------------------------------------------
					// |int| int | String | int |
					// |7 | taille address | address | port |
					// ---------------------------------------------------

					statusExchange = StatusExchange.CONNECTED_TO_PRV;
					// TODO : on doit accede a l'autre client poru envoyer la
					// trame sur ça socket et aussi pour changer son statu

					statusTreatment = StatusTreatment.TYPE_READING;
					break;
				case REF_CO_PRV_CS:// ce la session du client 2 qui reçoi la trame.
					
					if (/*(!dataPacketRead.getLoginSrc().equals(login))
							||*/ (id != dataPacketRead.getId())) {
						System.out.println("CLOSE AN REJECET CLIENT "+login);
						// si il s'agit d'une usurpation d'identité on ferme la
						// connection
						// TODO: close
						CloseAnRejectClient(login);
					}
					System.out.println("recepetion de REF_CO_PRV_CS");
					writePacketToSend(dataPacketRead, TypePacket.REF_CO_PRV_SC,
							out);

					/*
					 * 
					 * ----------------------------------------- |int| int |
					 * String | id | 6 | taille pseudo c1 |pseudo c1| long
					 * -----------------------------------------
					 */

					

					statusTreatment = StatusTreatment.TYPE_READING;
					break;
				case MESSAGE:
					/*
					 * 
					 * ----------------------------------------------------------
					 * --------------------------------- |int | int | String |
					 * long | int | String | |15 | taille pseudo |pseudo | id |
					 * taille message | Message|
					 * --------------------------------
					 * --------------------------
					 * ---------------------------------
					 */

					if ((!dataPacketRead.getLoginSrc().equals(login))
							|| (id != dataPacketRead.getId())) {
						// si il s'agit d'une usurpation d'identité on ferme la
						// connection
						// TODO: close
						CloseAnRejectClient(login);
					}
					writePacketToSend(dataPacketRead, TypePacket.MESSAGE, out);

					statusTreatment = StatusTreatment.TYPE_READING;
					// Writters.aquitPrivateConnection(TypePacket.MESSAGE,
					// loginDest, port, out);
					// Writters.sendMessage(sc, src, data.getLoginDst()/*size
					// message*/);

					break;

				}
				System.out.println("statusExchange : " + statusExchange);
				System.out.println("statusTreatment : " + statusTreatment);

			}

		}

	}

	public ServerMultiChatTCPNonBlockingWithQueueGoToMatou3(int port)
			throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		System.out.println("ALLOCATION "+Thread.currentThread().getStackTrace()[1].getLineNumber());
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		Set<SelectionKey> selectedKeys = selector.selectedKeys();

		while (!Thread.interrupted()) {

			System.out.println("avant selecte");
			selector.select();
			System.out.println("aprés selecte");
			processSelectedKeys();
			selectedKeys.clear();
		}
	}

	private void processSelectedKeys() throws IOException {

		for (SelectionKey key : selectedKeys) {
			Attachement at = (Attachement) key.attachment();
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);// on ne catrch pas cette exception parce que si
								// le accept
				// pete c'et que le serveur est mor
			}
			System.out.println("aaaaa0");

			try { // on la catch ici car on arrete pas le serveur pour ça
				if (key.isValid() && key.isWritable()) {
					System.out.println("START DOWRITE");
					at.doWrite(key);
				}
				System.out.println("aaaaa1");
				if (key.isValid() && key.isReadable()) {
					System.out.println("START DOREAD");
					at.doRead(key);
				}
				System.out.println("aaaaa2");
			} catch (Exception e) {
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
		System.out.println("ALLOCATION "+Thread.currentThread().getStackTrace()[1].getLineNumber());
		sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE,
				new Attachement(sc));// ALLOC : obligatoire

	}

	

	

	
	public static void main(String[] args) throws NumberFormatException,
			IOException {
		
		System.out.println("ALLOCATION line "+Thread.currentThread().getStackTrace()[1].getLineNumber());// ALLOC : obligatoire
		new ServerMultiChatTCPNonBlockingWithQueueGoToMatou3(
				Integer.parseInt(args[0])).launch();

	}

}