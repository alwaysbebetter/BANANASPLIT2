package fr.upem.net.tcp.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import fr.upem.net.tcp.clientProtocol.Format;
import fr.upem.net.tcp.clientProtocol.Readers;
import fr.upem.net.tcp.clientProtocol.Writters;
import fr.upem.net.tcp.server.ServerTCP.TypePacket;

public class ClientTCP {

	private SocketChannel currentChannel;

	// General message come here
	private final SocketChannel generalChannel;

	// Need to accept connection
	private final ServerSocketChannel ssc;

	// if we want to connect again.
	private final InetSocketAddress remoteAddress;

	private final String myName,myIP;

	private Scanner sc;
	// To prevent identity stealing.
	private final long clientID;
	// Those 3 variables are shared between thread to notify others
	// So we use synchronized on them

	private final Object lock = new Object();
	// delay in second
	private final int maxTime = 10,maxConnection = 5;
	private Thread generalListener, timeout;
	private final ConcurrentHashMap<String, PrivateChannel> map;

	class PrivateChannel {
		private SocketChannel pc = null;
		private SocketChannel fc = null;
		private Thread privateListener, fileListener, fileWritter;
		private String fileToSend, fileReceived;
		private boolean receivedFile = false, fileSending = false, acceptFile = false;
		private boolean receivedInvite = false;
		private final String name;
		private Object lockPrivate = new Object(), lockReadFile = new Object(), lockWriteFile = new Object();
		// use to refuse automatically request
		int countConnection = 0;

		public PrivateChannel(String name) {
			this.name = name;
			this.init();
		}

		private void init() {
			this.privateListener = new Thread(() -> {

				while (!Thread.interrupted()) {
					try {
						synchronized (lockPrivate) {
							while (this.pc == null)
								lockPrivate.wait();
						}

						TypePacket packet = TypePacket.values()[Readers.readByte(this.pc)];
						long size;
						switch (packet) {

						// Exchange address and port between the two clients
						// Here we are c2, we open the channel and connect then
						// send our address and port.
						case ASC_CO_FIL_CC:
							System.out.println("Demande fichier reçu");
							this.fc = SocketChannel.open();
							this.fc.connect(Readers.readAddress(this.pc));
							System.out.println("Connexion pour envoi de fichier établie !");
							currentChannel = this.pc;
							System.out.println(
									"Vous êtes maintenant sur le chat privé, tapez /g pour revenir sur le chat normal.");
							break;
						
						case ACC_CO_FIL_CC:
							this.fc.connect(Readers.readAddress(this.pc));
							currentChannel = this.pc;
							System.out.println(
									"Vous êtes maintenant sur le chat privé, tapez /g pour revenir sur le chat normal.");
							break;

						// case we have a demand for file.
						case ASC_SEND_FIL_CC:
							synchronized (lock) {
								receivedFile = true;
							}
							size = Readers.readLong(this.pc);
							this.fileReceived = Readers.readString(this.pc);
							System.out.println(
									name + " veut vous envoyer " + this.fileReceived + " (" + size + " bytes)");
							System.out
									.println("Tapez /yes " + name + " pour accepter ou /no " + name + " pour refuser.");
							break;
						// case the person has accepted our demand
						case ACC_SEND_FIL_CC:
							synchronized (lockWriteFile) {
								acceptFile = true;
								lockWriteFile.notify();
							}
							System.out.println("Demande accepté, envoi en cours...");
							// fileWritter.start();
							;
							break;
						// case the person has refused our demand
						case REF_SEND_FIL_CC:
							fileSending = false;
							System.out.println("Votre demande d'envoi de fichier a été refusé.");
							break;
						case MESSAGE:
							Readers.readPrivateMessage(this.pc);
							break;
						default:
							System.err.println("Unexpected packet, bad client in private chat");
							throw new IOException("Unexpected Packet");
						}
					} catch (IOException e) {
						silentlyClosePrivate();
					} catch (InterruptedException ie) {
						System.err.println("Stop listening on privateChannel.");
						Thread.currentThread().interrupt();
					}

				}

			});
			privateListener.start();

			this.fileListener = new Thread(() -> {

				while (!Thread.interrupted()) {
					try {
						// Wait until a connection is make
						synchronized (lockReadFile) {
							while (this.fc == null || !receivedFile)
								lockReadFile.wait();
						}
						Readers.readFile(this.fc, fileReceived);
						// Share with general Thread and main, so use simple
						// lock
						synchronized (lock) {
							receivedFile = false;
						}
						System.out.println("Fichier reçu");
					} catch (IOException e) {
						System.err.println("Deconnexion pour l'envoi de fichier.");
						silentlyClosePrivate();
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
					}
				}

			});
			fileListener.start();

			this.fileWritter = new Thread(() -> {

				while (!Thread.interrupted()) {
					try {
						synchronized (lockWriteFile) {
							while (!fileSending || !acceptFile)
								lockWriteFile.wait();
						}

						Writters.sendFile(this.fc, Paths.get(fileToSend));
						synchronized (lockWriteFile) {
							fileSending = false;
						}
						System.out.println("Envoi terminé");
					} catch (IOException e) {
						silentlyClosePrivate();
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
					}
				}
			});
			fileWritter.start();
		}

		private void silentlyClosePrivate() {
			if (currentChannel == this.pc)
				currentChannel = generalChannel;
			silentlyClose(pc);
			silentlyClose(fc);
			stop();
			map.remove(this.name);
			System.out.println("Connection privé fermé.");
		}

		public boolean isConnect() {
			if (this.pc != null)
				return pc.isConnected();
			return false;
		}

		private void stop() {
			privateListener.interrupt();
			fileListener.interrupt();
			fileWritter.interrupt();
		}

	}

	/**
	 * 
	 * @param serverAdress
	 * @param serverPort
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public ClientTCP(String serverAdress, int serverPort) throws IOException {
		myIP = Format.getMyIP();
		if(myIP.isEmpty()){
			throw new UnknownHostException("Problem getting your ip");
		}
			
		generalChannel = SocketChannel.open();
		this.remoteAddress = new InetSocketAddress(serverAdress, serverPort);
		generalChannel.connect(this.remoteAddress);
		ssc = ServerSocketChannel.open();
		ssc.bind(null);
		this.map = new ConcurrentHashMap<>();
		this.sc = new Scanner(System.in);
		
		myName = askName();
		this.clientID = Readers.readLong(generalChannel);
		System.out.println("Your name is " + myName);
		currentChannel = generalChannel;
		initListener();
	}

	private void initListener() {
		this.generalListener = new Thread(() -> {

			while (!Thread.interrupted()) {
				try {

					TypePacket packet = TypePacket.values()[Readers.readByte(generalChannel)];
					String name;
					PrivateChannel channel;
					System.out.println(packet.getValue());
					switch (packet) {

					case ASC_CO_PRV_SC:

						name = Readers.readString(generalChannel);
						if(map.size() >= this.maxConnection){
							Writters.denyPrivateConnection(generalChannel, clientID, name, myName);
							break;
						}

						if (map.get(name) == null) { 
							channel = new PrivateChannel(name);
							channel.receivedInvite = true;
							map.put(name, channel);

							System.out.println(name + " vous a invité en chat privé.");
							System.out
									.println("Tapez /yes " + name + " pour accepter ou /no " + name + " pour refuser.");
						} else
							System.out.println(name + " est déjà en invitation ou connecté.");
						break;

					case ACC_CO_PRV_SC:
						

						// In this case we are c1 because we are not yet
						// connected like c2.
						// c2 has received our demand so his privateChannel is
						// open.
						// We check destName to check if we have invite someone,
						// avoid late accept.
						name = Readers.readString(generalChannel);
						InetSocketAddress address = Readers.readAddress(generalChannel);

						// Case we receive an acceptation but didn't invite him
						if (!map.containsKey(name))
							break;

						channel = map.get(name);
						System.out.println("Acceptation par " + name);
						if (channel != null) {
							if (null == channel.pc) {
								System.out.println("Votre demande a été accepté par " + name + " !");
								// Here we receive the server address of c2 so
								// we can connect to him.
								synchronized (channel.lockPrivate) {
									System.out.println("Connexion à l'autre client.");
									channel.pc = SocketChannel.open(address);
									System.out.println("Connexion établie");
									Writters.acceptPrivateConnection(generalChannel, clientID, name, myIP,ssc.socket().getLocalPort(), myName);
									channel.lockPrivate.notify();
								}
								// privateListener.start();

								System.out.println("Connexion privé établie !");
								Writters.askPrivateFileConnection(channel.pc, TypePacket.ASC_CO_FIL_CC, myIP,ssc.socket().getLocalPort());
								System.out.println("Demande de connexion pour envoyer des fichiers...");
								channel.fc = ssc.accept();
								System.out.println("Connexion pour envoi de fichier établie !");
								System.out.println(
										"Vous êtes maintenant sur le chat privé, tapez /g pour revenir sur le chat normal.");
								currentChannel = channel.pc;

							} else if ((null != channel.pc) && channel.receivedInvite) {
								synchronized (channel.lockPrivate) {
									System.out.println("Attente de connexion de l'autre client.");
									channel.pc = ssc.accept();
									System.out.println("Connexion établie !");
									channel.lockPrivate.notify();
									// Readers.readAddress(generalChannel);

								}
							} else if (null != channel.pc) {
								System.out.println("Erreur connexion déjà établie avec " + name);
								// Readers.readAddress(generalChannel);
							}
						}

						// case we are already connected, destName is not null

						break;

					case REF_CO_PRV_SC:
						name = Readers.readString(generalChannel);
						map.remove(name);
						System.out.println("Votre demande a été refusé par " + name);
						break;

					case MESSAGE:
						Readers.readMessage(generalChannel);
						break;
					default:
						System.err.println("Unexpected packet");
						throw new IOException("Unexpected Packet");
					}
				} catch (IOException e) {
					System.err.println("Deconnexion du server.");
					silentlyCloseClient();
				}

			}

		});
		generalListener.start();

		this.timeout = new Thread(() -> {
			try {
				while (!Thread.interrupted()) {

					for (PrivateChannel p : map.values()) {
						// check part
						if (p.pc != null) {
							p.countConnection = 0;
							continue;
						} else
							p.countConnection++;

						// Treatment part
						if (p.countConnection == this.maxTime) {
							// Refuse automatically an invitation, if we waiting
							// someone
							// he will just be remove after the delay
							synchronized (lock) {
								if (p.receivedInvite) {
									System.out.println("Refus automatique.");
									Writters.denyPrivateConnection(generalChannel, clientID, p.name, myName);
									p.receivedInvite = false;
								}
							}
							// Remove the person
							this.map.remove(p.name);
							System.out.println("Le délai est dépassé, connexion avec " + p.name + " interrompu.");
							p.countConnection = 0;
						}
					}
					// Wait 1 second to check again
					Thread.sleep(1000);
				}

			} catch (IOException ioe) {

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

		});
		this.timeout.start();

	}

	private String askName() throws IOException {

		
		String name;
		while (true) {
			System.out.println("What is your pseudo ?");
			if (sc.hasNextLine()) {
				name = sc.nextLine();
				// refuse name with space
				if (name.contains(" ")) {
					System.out.println("Les espaces sont interdis.");
					continue;
				}
				Writters.requestName(generalChannel, name);
				if (Readers.nameAccepted(generalChannel)) {

					return name;
				}

			}
		}

	}

	private void treatCommand(String line) throws IOException {
		String command, argument[];
		PrivateChannel channel;
		if (line.startsWith("/")) {
			argument = line.split(" ", 4);
			command = argument[0];
			switch (command) {

			// See people connected
			case "/log":
				System.out.println("Voici les personnes connectés :");
				// Writters.askConnected(generalChannel,clientID,myName);
				return;
				
			case "/who":
				if(map.isEmpty())
					System.out.println("Personne connecté avec vous.");
				else
					for(PrivateChannel p : map.values()){
						if(p.pc == null)
							System.out.println(p.name + " en attente de connexion.");
						else
							System.out.println(p.name + " connecté avec vous.");
						
					}
				System.out.println("total : " + map.size());
				return;

			// Invite someone
			case "/invite":
				// Take the two first word
				// Example /invite Bob
				if(map.size() >= this.maxConnection){
					System.out.println("Le maximum de connexion est atteinte, désolé.");
					return;
				}

				if (argument.length >= 2) {

					if (map.get(argument[1]) == null) {
						map.put(argument[1], new PrivateChannel(argument[1]));
						System.out.println("Demande de chat privé à " + argument[1]);
						// this.destName = argument[1];
						Writters.askPrivateConnection(generalChannel, clientID, myName, argument[1]);
					} else
						System.out.println(argument[1] + " est déjà en attente de connexion ou déjà connecté");
				}

				else
					System.out.println("Précisez la personne à inviter !");
				return;

			// Send file
			case "/send":
				if (argument.length >= 3) {
					channel = map.get(argument[2]);
					// If we are connected
					if (channel != null && channel.pc != null) {
						// If we are not already sending a file
						if (!channel.fileSending) {
							// Test if the file exist before asking to send
							Path path = Paths.get(argument[1]);
							if (path.toFile().exists()) {
								synchronized (channel.lockWriteFile) {
									channel.fileSending = true;
								}
								channel.fileToSend = argument[1];
								Writters.askToSendFile(channel.pc, path);
								System.out.println("Demande d'envoi du fichier " + argument[1] + " en cours...");
							} else {
								System.out.println("Le fichier " + argument[1] + " n'existe pas ! Demande annulé.");
							}
						} else {
							System.out.println("Le fichier " + channel.fileToSend + " est en attente d'une réponde de "
									+ argument[2]);
						}
					} else if (argument[2].isEmpty()) {
						System.out.println("Veuillez préciser la personne à qui l'envoyer.");
					}
					// channel is null
					else {
						System.out.println("Veuillez vous connecter en privé à " + argument[2]);
					}
				} else
					System.out
							.println("Précisez un fichier à envoyer et la personne !\nExemple : /send monfichier Max");
				return;

			// Leave private chat
			case "/quit":
				if (argument.length >= 2) {
					channel = map.get(argument[1]);
					if ((channel != null)  && (channel.pc != null)) {
						System.out.println("Vous avez quitté le chat privé.");
						channel.silentlyClosePrivate();
						
					} else
						System.out.println("Vous n'avez pas de discussion privé en cours avec " + argument[1] + ".");
				} else
					System.out.println("Précisez la personne avec qui vous voulez coupé contact.");
				return;

			case "/leave":
				silentlyCloseClient();
				return;

			// accept an invite
			case "/yes":
				if (argument.length >= 2) {
					channel = map.get(argument[1]);
					if (channel != null) {

						// In this case we are c2, and accept the connection to
						// c1.
						if (channel.receivedInvite && (channel.pc == null)) {
							// We prepare the channel here and send our address
							// and port to c1.
							// We need to connect to c1 after, the tread
							// generalListener will do this.

							channel.pc = SocketChannel.open();
							Writters.acceptPrivateConnection(generalChannel, clientID, argument[1], myIP,ssc.socket().getLocalPort(), myName);

							System.out.println("Vous avez accepté l'invitation");

						} else if (channel.receivedFile && (channel.pc != null)) {
							Writters.acceptFile(channel.pc);
							System.out.println("Vous avez accepté le fichier.");
							// Start the thread to receive file, the tread stop
							// after reading.
							// fileListener.start();
							synchronized (channel.lockReadFile) {
								channel.lockReadFile.notify();
							}
						} else
							System.out.println("Vous n'avez pas reçu d'invitation");

					} else
						System.out.println("Vous n'avez pas reçu de demande de " + argument[1]);
				} else {
					System.out.println("Précisez la personne que vous voulez accepter.");
				}

				return;

			// Deny an invite
			case "/no":
				if (argument.length >= 2) {
					channel = map.get(argument[1]);
					if (channel != null) {

						if (channel.receivedInvite && (channel.pc == null)) {
							Writters.denyPrivateConnection(generalChannel, clientID, argument[1], myName);
							// channel.receivedInvite = false;
							map.remove(argument[1]);
							System.out.println("Vous avez refusé l'invitation.");
						} else if (channel.receivedFile && (channel.pc != null)) {
							Writters.refuseFile(channel.pc);
							channel.receivedFile = false;
							System.out.println("Vous avez refusé le fichier.");
						} else
							System.out.println("Vous n'avez pas reçu d'invitation.");
					}

					else
						System.out.println("Vous n'avez pas reçu de demande de " + argument[1]);

				} else
					System.out.println("Précisez la personne à qui vous voulez envoyer le refus.");
				return;

			// switch to private message
			case "/p":
				if (argument.length >= 2) {
					channel = map.get(argument[1]);
					if (channel != null && channel.isConnect()) {
						if (currentChannel == channel.pc) {
							System.out.println("Vous êtes déjà sur le chat privé avec " + channel.name);
						} else {
							currentChannel = channel.pc;
							System.out.println("Vous avez basculé sur le chat privé avec " + channel.name);
						}
					} else
						System.out.println("Pas de chat privé avec cette personne.");
				} else
					System.out.println("Précisez la personne avec qui vous voulez parlé");
				return;

			case "/g":
				// We compare reference
				if (currentChannel == generalChannel) {
					System.out.println("Vous êtes déjà sur le chat général.");
				} else {
					currentChannel = generalChannel;
					System.out.println("Vous avez basculé sur le chat général.");
				}
				return;

			case "/help":
				printCommand();
				return;

			default:

				break;

			}

		}

		if (currentChannel == generalChannel)
			Writters.sendMessage(generalChannel, clientID, myName, line);
		else
			Writters.sendPrivateMessage(currentChannel, myName, line);

	}

	private void printCommand() {
		System.out.println("Voici la liste des commandes :\n");
		System.out.println("/log : Pas encore disponible.");
		System.out.println("/who : Affiche les personnes avec qui vous êtes connecté.");
		System.out.println("/invite name : demande une connexion privé à \"name\".");
		System.out.println("/leave : quitter le client.");
		System.out.println("/send file name: envoi le fichier \"file\" à la personne \"name\" connecté en privé.");
		System.out.println("/quit name : quitter le chat privé de \"name\".");
		System.out.println("/yes name : accepter une demande de name.");
		System.out.println("/no name : refuser une demande de name.");
		System.out.println("/p name: basculer sur le chat privé de la personne \"name\".");
		System.out.println("/g : basculer sur le chat général.");
		System.out.println("/help : afficher la liste des commandes.\n");
	}

	/**
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void launch() throws IOException, InterruptedException {

		System.out.println("Client is ready.");
		System.out.println("Tapez /help pour voir les commandes disponibles !");
		String line;
		try {
			while (true) {

				if (sc.hasNextLine()) {
					// First Read message

					line = sc.nextLine();

					// Treat command or send message
					treatCommand(line);
				}

			}
		} finally {
			silentlyCloseClient();
			sc.close();
		}
	}

	private void silentlyCloseClient() {
		if (generalChannel != null)
			System.out.println("Le client va fermé.");
		silentlyClose(generalChannel);
		// silentlyClosePrivate();
		map.clear();

		System.exit(0);

	}

	private void silentlyClose(SocketChannel socket) {
		if (socket != null)
			try {
				socket.close();
				socket = null;
			} catch (IOException e) {
				// Ignore
			}

	}
	
	private static void usage(){
		System.out.println("usage java -jar clientTCP.jar \"IP du server\" \"port du server\"");
		System.exit(0);
	}

	public static void main(String[] args)
			throws NumberFormatException, UnknownHostException, IOException, InterruptedException {
		if(args.length != 2)
			usage();
		new ClientTCP(args[0], Integer.parseInt(args[1])).launch();
	}
}