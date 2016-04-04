package fr.upem.net.tcp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.time.DayOfWeek;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import fr.upem.net.logger.Loggers;
import fr.upem.net.tcp.server.ServerTcpNonBlocking.TypePacket;

public class ServerMultiChatTCPNonBlockingWithQueueGoToMatou3 {

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final ConcurrentHashMap<Integer, SelectionKey> map = new ConcurrentHashMap<>();
	private int co = 0;
	static private final int BUFSIZ = 200;
	public static final Charset UTF_8 = Charset.forName("utf-8");
	public final static int SRC_DATA = 0, DEST_DATA = 1, DEST_DATA_SRC = 2;

	private enum StatusTreatment {

		TYPE_READING, TYPE_KNOWN, CHOOSE_TREATING, READ_LOGIN, END_READING, END_TREATMENT, REFILL, ERROR, DONE, READER_KNOWN, DATA_PACKET_KNOWN

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

	private class Attachement {

		ByteBuffer in;
		boolean isClosed = false;
		LinkedList<ByteBuffer> queue = new LinkedList<>();
		public DataPacketRead dataPacketRead;
		StatusExchange statusExchange = StatusExchange.WAITING_TO_CO_SERV;
		StatusTreatment statusTreatment = StatusTreatment.TYPE_READING;
		TypePacket typeLastPacketReceiv;
		Reader readerACC_CO_PRV_CS, readerASC_CO_PRV_CS, readerREF_CO_PRV_CS,readerASC_CO_SERV,
				readerMESSAGE, currentReader;

		public Attachement() {

			in = ByteBuffer.allocate(BUFSIZ * 4);
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

		public int getInterest() {
			int interest = 0;// initialize
			if (in.position() > 0) {
				interest = interest | SelectionKey.OP_WRITE;
			}
			if (!isClosed) {
				interest |= SelectionKey.OP_READ;
			}
			return interest;

		}

		private boolean isValideTypePacket(byte typePacket) {
			if (typePacket < 0 || typePacket > 15)
				return false;
			return true;
		}

		
		private void readType() {// CHECKED
			if ((statusTreatment == StatusTreatment.TYPE_READING)
					&& (in.position() >= 1)) {
				
				//System.out.print("Received format packet:");Loggers.test(in);//TODO : displaying to debbug, after remove it
				System.out.println("statusTreatement : "+statusTreatment);//TODO : displaying to debbug, after remove it
				in.flip();
				// get type
				typeLastPacketReceiv = TypePacket.values()[in.get()];
				System.out.println("size ----: "+in.getInt());
				in.compact();
				// change status
				if (!isAnExpectedTypePacket(typeLastPacketReceiv)) {

				}
				
				System.out.println("readType() -> "+typeLastPacketReceiv);//TODO : displaying to debbug, after remove it
				statusTreatment = StatusTreatment.TYPE_KNOWN;
				System.out.println("statusTreatement : "+statusTreatment);//TODO : displaying to debbug, after remove it
			}
		
			
		}

		// CHECKED but need to be finsh ( default with close 
		// socket and remove client ( make a method closing socket and deleteing client )
		// TO CHECK the updating of reader !!
		public void findReader() {

			System.out.println("findReader -> reader"+typeLastPacketReceiv);//TODO : displaying to debbug, after remove it
			if (statusTreatment == StatusTreatment.TYPE_KNOWN) {
				switch (typeLastPacketReceiv) {
				case ASC_CO_SERV:
					if (readerASC_CO_SERV == null) {
						readerASC_CO_SERV = new ReaderString(SRC_DATA);
					}
					currentReader = readerASC_CO_SERV;
					
					break;

				case ASC_CO_PRV_CS:// Code : 3

					if (readerASC_CO_PRV_CS == null) {
						readerASC_CO_PRV_CS = new ReaderString(new ReaderLong(
								new ReaderString(SRC_DATA)), DEST_DATA);
					}
					currentReader = readerASC_CO_PRV_CS;
					break;
				case ACC_CO_PRV_CS:// Code : 5

					if (readerACC_CO_PRV_CS == null) {
						readerACC_CO_PRV_CS = new ReaderInt(new ReaderString(
								new ReaderLong(new ReaderString(SRC_DATA)),
								DEST_DATA_SRC));
					}
					currentReader = readerACC_CO_PRV_CS;
					break;
				case REF_CO_PRV_CS:// Code : 5
					
					if (readerREF_CO_PRV_CS == null) {
						readerREF_CO_PRV_CS = new ReaderLong(new ReaderString(
								SRC_DATA));
					}
					currentReader = readerREF_CO_PRV_CS;
					break;
				case MESSAGE:// Code :15

					if (readerMESSAGE == null) {
						readerMESSAGE = new ReaderString(new ReaderLong(
								new ReaderString(SRC_DATA)), DEST_DATA);
					}
					currentReader = readerMESSAGE;
					break;

				default: // close
					System.out.println("UNKNOWN PACKET -> close and remove!!");//TODO : displaying to debbug, after remove it);
				}
				statusTreatment = StatusTreatment.READER_KNOWN;
				System.out.println("statusTreatement : "+statusTreatment);//TODO : displaying to debbug, after remove it
			}
			

		}
		
		
		
		public void applyReader() {
			
			if (statusTreatment == StatusTreatment.READER_KNOWN) {

				switch (currentReader.process(in)) {
				case DONE://TRAITEMENT
			
					System.out.println("applyReader -> DONE");//TODO : displaying to debbug, after remove it
					statusTreatment = StatusTreatment.DATA_PACKET_KNOWN;
					// reset Datzpz
					// dataPacketRead.setTypePacket(typeLastPacketReceiv);
					break;
				case ERROR:
					System.out.println("applyReader -> ERROR");//TODO : displaying to debbug, after remove it
					// TODO : close
					break;
				case REFILL:
					System.out.println("applyReader -> REFILL");//TODO : displaying to debbug, after remove it
					return;
				}
			}
		}
		public void treatData() {
			if( statusTreatment == StatusTreatment.DATA_PACKET_KNOWN ){
				System.out.println("statusTreatment -> "+statusTreatment);//TODO : displaying to debbug, after remove it
				System.out.println("treatData -> "+dataPacketRead.toString());//TODO : displaying to debbug, after remove it
				System.exit(1);
				dataPacketRead = currentReader.get();
				in.clear();
				in.putInt(dataPacketRead
						.getSizeLoginSrc());
				in.put(UTF_8
						.encode(dataPacketRead.getLoginSrc()));
			}
		}
	}

	public ServerMultiChatTCPNonBlockingWithQueueGoToMatou3(int port)
			throws IOException {
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
			if (key2.isValid() && (key2.channel() instanceof SocketChannel)
					&& (!key2.equals(key))) {
				theAttachement.in.flip();
				SocketChannel sch = (SocketChannel) key2.channel();
				sch.write(theAttachement.in);

			}

		}
	}


	

	
	private void doRead(SelectionKey key) throws IOException {
		Attachement theAttachement = (Attachement) key.attachment();

		SocketChannel client = (SocketChannel) key.channel();

		if (-1 == client.read(theAttachement.in)) {
			theAttachement.isClosed = true;
			if (theAttachement.in.position() == 0) {

				client.close();
			}

		}

		theAttachement.readType();
		theAttachement.findReader();
		theAttachement.applyReader();
		theAttachement.treatData();
		
		
		
		key.interestOps(theAttachement.getInterest());
	}




	private void doWrite(SelectionKey key) throws IOException {
		SocketChannel client = (SocketChannel) key.channel();
		Attachement theAttachement = (Attachement) key.attachment();

		// faire le techeck sur la taille avant et il fatu faire en sorte que la
		// taille n'excede jamasi celel du buffer qu'on a allouer comme ça pas
		// besoin de reallouer.

		publish(key, theAttachement);

		theAttachement.in.compact();// pour bien se repositionner sans ecraser
									// ce que l'on a lu
		if (theAttachement.isClosed) {
			client.close();
			theAttachement.isClosed = true;
		}

		key.interestOps(theAttachement.getInterest());
	}

	public static void main(String[] args) throws NumberFormatException,
			IOException {
		new ServerMultiChatTCPNonBlockingWithQueueGoToMatou3(
				Integer.parseInt(args[0])).launch();

	}

}