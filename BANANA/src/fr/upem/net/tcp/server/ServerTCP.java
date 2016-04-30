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

import fr.upem.net.tcp.clientProtocol.Format;
import fr.upem.net.tcp.server.Reader.StatusProcessing;

public class ServerTCP {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final ConcurrentHashMap<String, Attachement> map = new ConcurrentHashMap<>();
	static private final int BUFSIZ = 200, REMAINING_TRY = 3;
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

		//Used by enum
		@SuppressWarnings("unused")
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

	public enum StatusWriting {
		RETRIEVE_AND_WRITE(0), WRITE_ONLY(1), CURRENT_WRITING(2), CURRENT_WRITING_START(
				3);
		private final byte value;

		public byte getValue() {
			return value;
		}

		private StatusWriting(int value) {
			this.value = (byte) value;
		}
	}

	private class Attachement {
		SelectionKey key;
		ByteBuffer in, out;
		boolean isClosed = false;
		int remainingTry = REMAINING_TRY;
		private static final int MAX_SIZE_QUEUE = 1024;
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
		StatusWriting statusWriting = StatusWriting.RETRIEVE_AND_WRITE;

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
					// || typePacket.equals(TypePacket.ACC_CO_PRV_CS)

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

			if (out.position() > 0 || (!queue.isEmpty())) {
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
			// : desalouer
			if (map.get(login) != null) {
				map.remove(login);
			}
		}

		private void readType() {// CHECKED
			//System.out.println("coco");
			if ((statusTreatment == StatusTreatment.TYPE_READING)
					&& (in.position() >= 1)) {
				in.flip();
				// get type
				typeLastPacketReceiv = TypePacket.values()[in.get()];

				in.compact();
				// change status
				if (!isAnExpectedTypePacket(typeLastPacketReceiv)) {
					CloseAnRejectClient(login);
				}

			statusTreatment = StatusTreatment.TYPE_KNOWN;
				//System.out.println("statusTreatement : " + statusTreatment);//
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
					dataPacketRead.reset();
				}
					switch (typeLastPacketReceiv) {
				case ASC_CO_SERV:
					if (readerASC_CO_SERV == null) {
						readerASC_CO_SERV = new ReaderString(SRC_DATA,
								typeLastPacketReceiv);
					}
					currentReader = readerASC_CO_SERV;

					break;

				case ASC_CO_PRV_CS:// Code : 3

					if (readerASC_CO_PRV_CS == null) {
						readerASC_CO_PRV_CS = new ReaderString(
								new ReaderLong(new ReaderString(SRC_DATA,
										typeLastPacketReceiv)), DEST_DATA);
					}
					currentReader = readerASC_CO_PRV_CS;
					break;
				case ACC_CO_PRV_CS:// Code : 5

					if (readerACC_CO_PRV_CS == null) {
						readerACC_CO_PRV_CS = new ReaderString(new ReaderInt(
								new ReaderString(new ReaderLong(
										new ReaderString(DEST_DATA,
												typeLastPacketReceiv)),
										SRC_DATA_ADR)), SRC_DATA);
					}
					currentReader = readerACC_CO_PRV_CS;
					break;
				case REF_CO_PRV_CS:// Code : 6

					if (readerREF_CO_PRV_CS == null) {
						readerREF_CO_PRV_CS = new ReaderString(new ReaderLong(
								new ReaderString(DEST_DATA,
										typeLastPacketReceiv)), SRC_DATA);
					}
					currentReader = readerREF_CO_PRV_CS;
					break;
				case MESSAGE:// Code :15

					if (readerMESSAGE == null) {
						readerMESSAGE = new ReaderString(
								new ReaderLong(new ReaderString(SRC_DATA,
										typeLastPacketReceiv)), DEST_DATA);
					}
					currentReader = readerMESSAGE;
					break;

				default: // close
					//System.out.println("UNKNOWN PACKET -> close and remove!!");//
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
				//System.out.println("statusTreatement : " + statusTreatment);//
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

					//System.out.println("applyReader -> DONE");// :
																// displaying to
																// debbug, after
																// remove it
					statusTreatment = StatusTreatment.DATA_PACKET_KNOWN;

					// reset Datzpz
					// dataPacketRead.setTypePacket(typeLastPacketReceiv);
					return StatusProcessing.DONE;
				case ERROR:
					//System.out.println("applyReader -> ERROR");// :
																// displaying to
																// debbug, after
																// remove it
					// : close
					CloseAnRejectClient(login);
					return StatusProcessing.ERROR;
				case REFILL:
					//System.out.println("applyReader -> REFILL");// :
																// displaying to
																// debbug, after
																// remove it
					return StatusProcessing.REFILL;
				}
			}
			return StatusProcessing.ERROR;//: find other solution
		}

		/*
		 * private void publish(SelectionKey key) throws IOException { for
		 * (SelectionKey key2 : selector.keys()) { if (key2.isValid() &&
		 * (key2.channel() instanceof SocketChannel) && (!key2.equals(key))) {
		 * out.flip(); SocketChannel sch = (SocketChannel) key2.channel();
		 * sch.write(out);
		 * 
		 * }
		 * 
		 * } }
		 */
		private void sendToOtherClient(Attachement at) {

			out.position(out.remaining());
			if (queue.size() >= MAX_SIZE_QUEUE) {
				queue.poll();// we remove the latest
			}
			at.queue.addLast(out.duplicate());// même reference, mee position ou
												// on demar mais ce qu'on fasur
												// les autre position n'influ
												// pas le reste

			at.key.interestOps(at.getInterest());
			// at.queue.addLast(out.duplicate());// même reference, mee position
			// ou on demar mais ce qu'on fasur les autre position n'influ pas le
			// reste

		}

		private void publish(SelectionKey key) throws IOException {
			for (SelectionKey key2 : selector.keys()) {
				if (key2.isValid() && (key2.channel() instanceof SocketChannel)
						&& (!key2.equals(key))) {
					out.flip();

					Attachement at = (Attachement) key2.attachment();

					sendToOtherClient(at);

					// key2.interestOps(at.getInterest());
					// at.getInterest();
					// : attachement diférent du null FAIRE LE TESTE AVANT

				}
			}
		}

		public void writeString(ByteBuffer bb, String s) {
			ByteBuffer tmp = UTF_8.encode(s);
			bb.putInt(tmp.remaining()).put(tmp);
		}

		public void writePacketToSend(DataPacketRead data,
				TypePacket typePacketToSend, ByteBuffer bb) {
			//System.out.println("TYPE " + typePacketToSend.getValue());
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
				//System.out.println("write in bb REF_CO_PRV_SC");
				writeString(bb, data.getLoginSrc());
				//writeString(bb, data.getLoginDst());
				// Do nothing
				break;
			case ACC_CO_PRV_SC:
				writeString(bb, data.getLoginSrc());// LA
				writeString(bb, data.getAdrSrc());
				bb.putInt(data.getPortSrc());

				break;
			case MESSAGE:

				writeString(bb, data.getLoginSrc());
				// login dst is here the message
				writeString(bb, data.getLoginDst());
				break;
			default : break;

			}
			//Loggers.test(bb);

		}

		private void doRead(SelectionKey key) throws IOException {

			SocketChannel client = sc;
			if (-1 == client.read(in)) {

				isClosed = true;
				if (in.position() == 0) {

					client.close();
				}

			}
			//System.out.println("INTEREST_OPS :" + getInterest());
			readType();
			findReader();
			if (StatusProcessing.ERROR == applyReader()) {
				return;
			}
			treatData();

			key.interestOps(getInterest());
		}

		// ByteBuffer outFromQueue = null; MOD X
		private void doWrite(SelectionKey key) throws IOException {

			Attachement at;

			// faire le techeck sur la taille avant et il fatu faire en sorte
			// que la
			// taille n'excede jamasi celel du buffer qu'on a allouer comme ça
			// pas
			// besoin de reallouer.
			if ((statusWriting != StatusWriting.CURRENT_WRITING_START)
					&& (statusWriting != StatusWriting.CURRENT_WRITING)
					&& ((!queue.isEmpty()) || (statusWriting == StatusWriting.WRITE_ONLY))) {

				ByteBuffer outFromQueue;// MOD X
				switch (statusWriting) {

				case RETRIEVE_AND_WRITE:

					outFromQueue = queue.peek();// MOD X
					outFromQueue.flip();// MOD X

					sc.write(outFromQueue);
					outFromQueue.compact();
					statusWriting = StatusWriting.WRITE_ONLY;
					break;
				case WRITE_ONLY:
					/* try{ */
					outFromQueue = queue.poll();// MOD X
					outFromQueue.flip();// MOD X
					sc.write(outFromQueue);
					if (outFromQueue.hasRemaining()) {
						// //System.out.println("UUUUUUUUUUUUUUUUUUUUUUUU");//System.exit(1);
						outFromQueue.compact();
						break;

					}
					outFromQueue = null;// pour la liberation
					statusWriting = StatusWriting.RETRIEVE_AND_WRITE;
					break;
				default : break;
				}

			} else {

				if (statusWriting != StatusWriting.CURRENT_WRITING) {
					statusWriting = StatusWriting.CURRENT_WRITING_START;
				}
				switch (typeLastPacketReceiv) {
				case ASC_CO_SERV:
					if (statusWriting == StatusWriting.CURRENT_WRITING_START) {
						out.flip();
						statusWriting = StatusWriting.CURRENT_WRITING;

					}

					sc.write(out);
					if (out.hasRemaining()) {

						return;

					}
					out.compact();

					break;
				case ACC_CO_PRV_CS:
				case REF_CO_PRV_CS:// TOCHECK
					//System.out.println("réponse a REF_CO_PRV_CS");

					at = map.get(dataPacketRead.getLoginDst());
					out.flip();
					sendToOtherClient(at);
					out.compact();// ,normalement la il est vide puisque c'est
									// un put qui est fait dans le send
					/*
					 * at = map.get(dataPacketRead.getLoginDst()); out.flip();
					 * at.sc.write(out); out.compact();
					 */

					//System.out.println("remaaaiinning :" + out.remaining());
					break;
				case ASC_CO_PRV_CS:

					if ((at = map.get(dataPacketRead.getLoginDst())) != null) {
						out.flip();
						sendToOtherClient(at);

					} else {// si le client envoie une demande un client qui
							// n'existe pas alors il ressevra une trame de refu
						if (statusWriting == StatusWriting.CURRENT_WRITING_START) {
							out.flip();
							statusWriting = StatusWriting.CURRENT_WRITING;
						}
						sc.write(out);
						if (out.hasRemaining()) {

							return;

						}

					}
					//System.out.println("remaaaiinning :" + out.remaining());
					out.compact();
					break;
				case MESSAGE:
					if (map.size() > 1) {
						publish(key);
					}

					// synthetethetic clear
					out.clear();
					out.position(out.remaining());
					out.compact();

					//System.out.println("remaaaiinning :" + out.remaining());
					break;
					
				default : break;

				}
				statusWriting = StatusWriting.RETRIEVE_AND_WRITE;

				//System.out.println("raaa");

				if (isClosed) {
					sc.close();
					isClosed = true;
				}

				key.interestOps(getInterest());

			}
		}

		public void treatData() {

			if (statusTreatment == StatusTreatment.DATA_PACKET_KNOWN) {
				//System.out.println("statusTreatment -> " + statusTreatment);
				dataPacketRead = currentReader.get();
				//System.out.println("Packet to treat :" + dataPacketRead);
				/*
				 * if (bbWaitingsToBeUsed.isEmpty()) return; DataPacketRead data
				 * = bbWaitingsToBeUsed.poll();
				 */

				TypePacket theTypePacket = TypePacket.values()[dataPacketRead
						.getTypePacket().getValue()];
				if (!isAnExpectedTypePacket(theTypePacket)) {/* close */
					//System.out.println("Is unexpectedTypePacket !!");// :
																		// delete
					CloseAnRejectClient(login);//: ET METTRE A NUL L LA
												// VARIABLE CLIENT POUR QU'ELLE
												// SOIT PRISE PAR LE GARBAGE
												// COLLECTORS
				}
				//System.out.println("SERVER WILL TREAT :" + theTypePacket);
				switch (theTypePacket) {
				case ASC_CO_SERV:// MODIF A ( modifier tout ça ).
					// je test la car ça pourrait êre faut au moment ou on le
					// recupere de lafile
					String loginToTest = dataPacketRead.getLoginSrc();

					if (!isAUniqLogin(loginToTest)) {
						// : if false login we refused connexion ?
						// appeler la fonction qui va remplir le out avec le
						// packet de refu ( et fermer la connection ? )
						writePacketToSend(dataPacketRead,
								TypePacket.REF_CO_SERV, out);
						//System.out.println("IS NOT UNIQUE LOGIN");// :
						if (--remainingTry <= 0) {
							// si non quand on fait la suppression du client en
							// caas d'exception on en supprime un autre
							// MODIF 1
							silentlyClose(sc);

						}
						statusTreatment = StatusTreatment.TYPE_READING;
						return;// and close se socket
					}
					// une fois que le login est assuré
					login = loginToTest;
					//: do we have to manage the unicity with a comparason

					id = rand.nextLong();

					// ; add the client, here ?
					map.put(login, this);

					// : Appeler la fonction qui va remplir le out avec
					// le paquet d'acceptation.

					writePacketToSend(dataPacketRead, TypePacket.ACC_CO_SERV,
							out);

					// change status of exchange

					statusExchange = StatusExchange.CONNECTED_TO_SERV;
					//: Verifier si c'est pas la qu'on change l'état
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
						//: close
						CloseAnRejectClient(login);
					}

					String loginDest = dataPacketRead.getLoginDst();
					if ((map.get(loginDest) == null)
							|| (login.equals(loginDest))) {
						//: ne rien faire car il se peut que le
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
						//System.out.println("LOGIN DOESN'T EXIST ");// :
						writePacketToSend(dataPacketRead,
								TypePacket.REF_CO_PRV_SC, out);
						// delete
						// : envoyer la trame de notification de reffu ( ce
						// couop, si, depuis le server )
					} else {

						// WRITTER
						// realBuildOut(TypePacket.ACC_CO_SERV);
						writePacketToSend(dataPacketRead,
								TypePacket.ASC_CO_PRV_SC, out);
						// statusExchange = StatusExchange.WAITING_TO_CO_PRV;
					}

					statusTreatment = StatusTreatment.TYPE_READING;

					break;

				case ACC_CO_PRV_CS:

					if (/*
						 * (!dataPacketRead.getLoginSrc().equals(login)) ||
						 */(id != dataPacketRead.getId())) {
						// si il s'agit d'une usurpation d'identité on ferme la
						// connection
						//: close
						CloseAnRejectClient(login);
					}

					// attention c'est l'adresse privé c'est pour ça que debase
					// le serveur ne la connait aps et qu'il la communique.
					/// dans le rapport il faudra bien mettre en avant ce
					// que gere le serveur, notemment il empeche l'usurapation
					writePacketToSend(dataPacketRead, TypePacket.ACC_CO_PRV_SC,
							out);

					// ON A CONNAISSANCE DU LOGIN ne confond pas avec la trame
					// qu'on compose !!!!!

					// --------------------------------------------------
					// |int| int | String | int |
					// |7 | taille address | address | port |
					// ---------------------------------------------------

					// statusExchange = StatusExchange.CONNECTED_TO_PRV;
					// : on doit accede a l'autre client poru envoyer la
					// trame sur ça socket et aussi pour changer son statu

					statusTreatment = StatusTreatment.TYPE_READING;
					break;
				case REF_CO_PRV_CS:// ce la session du client 2 qui reçoi la
									// trame.

					if (/*
						 * (!dataPacketRead.getLoginSrc().equals(login)) ||
						 */(id != dataPacketRead.getId())) {
						//System.out.println("CLOSE AN REJECET CLIENT " + login);
						// si il s'agit d'une usurpation d'identité on ferme la
						// connection
						//: close
						CloseAnRejectClient(login);
					}
					//System.out.println("recepetion de REF_CO_PRV_CS");
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
						//: close
						CloseAnRejectClient(login);
					}
					writePacketToSend(dataPacketRead, TypePacket.MESSAGE, out);

					statusTreatment = StatusTreatment.TYPE_READING;
				
					break;
					
					default: break;

				}

			}

		}

	}

	public ServerTCP(int port)
			throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		System.out.println("Server bound on " + Format.getMyIP() + " port : " + port);
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
	}

	public void clearUnconnectedClient() {
		for (String login : map.keySet()) {
			if (!map.get(login).sc.isConnected()) {
				map.remove(login);
			}
		}
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		Set<SelectionKey> selectedKeys = selector.selectedKeys();

		while (!Thread.interrupted()) {
			selector.select();
			clearUnconnectedClient();
			processSelectedKeys();
			selectedKeys.clear();
		}
	}

	private void processSelectedKeys() throws IOException {

		for (SelectionKey key : selectedKeys) {
			Attachement at = (Attachement) key.attachment();
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}


			try { 
				if (key.isValid() && key.isWritable()) {
					at.doWrite(key);
				}

				if (key.isValid() && key.isReadable()) {
					at.doRead(key);
				}
			} catch (IOException e) {
				
				if (map.get(at.login) != null) {
					map.remove(at.login);
				}
			} catch (Exception e) {
				if ((at.login != null) && (map.get(at.login) != null)) {
					map.remove(at.login);
				}
			}

		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		SocketChannel sc = serverSocketChannel.accept();
		if (sc == null)
			return; // In case, the selector gave a bad hint
		sc.configureBlocking(false);

		Attachement at = new Attachement(sc);
		SelectionKey key2 = sc.register(selector, SelectionKey.OP_READ
				| SelectionKey.OP_WRITE, at);
		at.key = key2;
	}
	
	private static void usage(){
		System.out.println("usage java -jar serverTCP.jar \"port du server\"");
		System.exit(0);
	}

	public static void main(String[] args) throws NumberFormatException,
			IOException {

		if(args.length != 1)
			usage();
		new ServerTCP(
				Integer.parseInt(args[0])).launch();

	}

}
