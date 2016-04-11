
package fr.upem.net.tcp.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.util.Scanner;

import fr.upem.net.logger.Loggers;
import fr.upem.net.tcp.protocol.Readers;
import fr.upem.net.tcp.protocol.Writters;


public class ClientTCPMatou {
	
	private SocketChannel currentChannel;
	
	//General message come here
	private final SocketChannel generalChannel;
	
	//Need to accept connection
	private final ServerSocketChannel ssc;
	
	//Private message come here
	private SocketChannel privateChannel = null;
	
	//Channel uses to download/upload file
	private SocketChannel fileChannel = null;
	private final InetSocketAddress remoteAddress;
	
	//List of folks connected
	//We use a Linked list because people will connect and disconnect(add/remove) often
	//private final LinkedList<String> listPeople = new LinkedList<>();
	
	private final String myName;
	private String destName;
	private String fileToSend,fileReceived;
	private Scanner sc ;
	//To prevent identity stealing.
	private final long clientID;
	//Those 3 variables are shared between thread to notify others
	//So we use synchronized on them
	private  boolean receivedInvite = false;
	private  boolean receivedFile = false,fileSending = false, acceptFile = false;
	
	private final Object lock = new Object(),lockPrivate = new Object(),lockReadFile = new Object(),lockWriteFile = new Object();
	private Thread generalListener, privateListener,fileListener,fileWritter;
	
	

	/**
	 * 
	 * @param serverAdress
	 * @param serverPort
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public ClientTCPMatou(String serverAdress, int serverPort)
			throws UnknownHostException, IOException {
		generalChannel = SocketChannel.open();
		this.remoteAddress = new InetSocketAddress(serverAdress, serverPort);
		generalChannel.connect(this.remoteAddress);
		ssc = ServerSocketChannel.open();
		ssc.bind(null);
		this.sc = new Scanner(System.in);
		myName = askName();
		this.clientID = Readers.readLong(generalChannel);
		System.out.println("Your name is " + myName);
		currentChannel = generalChannel;
		initListener();
	}
	
	private void initListener(){
		this.generalListener = new Thread( () -> {
			
				while(!Thread.interrupted()){
					try{
						byte id = Readers.readByte(generalChannel);
						String name;
						switch(id){
							
							case 4 : 
								synchronized(lock){
									this.receivedInvite = true;
								}
								if(privateChannel == null){
									this.destName = Readers.readDemand(generalChannel);
									System.out.println(destName + " vous a invité en chat privé.");
									System.out.println("Tapez /yes pour accepter ou /no pour refuser.");
								}
								else{
									System.out.println(Readers.readDemand(generalChannel) + " vous a invité mais vous"
											+ " êtes déjà connecté avec " + this.destName);
								}
								
								
								break;
								
							case 7 :
								
								name = Readers.readString(generalChannel);
								System.out.println(name + "frefez");
								System.out.println("destName "+ destName +" =? name "+name );
								InetSocketAddress inet = Readers.readAddress(generalChannel);
								
								
								if(!name.equals(destName))
									System.out.println("Acceptation d'une ancienne demande (" + name + ") refusé.");
								//In this case we are c1 because we are not yet connected like c2.
								//c2 has received our demand so his privateChannel is open.
								//We check destName to check if we have invite someone, avoid late accept.
								else if( (null == this.privateChannel) && (this.destName != null) && name.equals(destName)){
									//TODO Changer Trame et checker si le nom c'est le bon
									System.out.println("Votre demande a été accepté par " + destName +" !");
									//Here we receive the server address of c2 so we can connect to him.
									synchronized(lockPrivate){
										privateChannel = SocketChannel.open(inet);
										Writters.acceptPrivateConnection(generalChannel, clientID, destName, ssc, myName);
										lockPrivate.notify();	
									}
									//privateListener.start();
									
									System.out.println("Connexion privé établie !");
									Writters.askPrivateFileConnection(privateChannel,(byte)9,ssc);
									System.out.println("Demande de connexion pour envoyer des fichiers...");
									fileChannel = ssc.accept();
									System.out.println("Connexion pour envoi de fichier établie !");
									System.out.println("Vous êtes maintenant sur le chat privé, tapez /g pour revenir sur le chat normal.");
									currentChannel=privateChannel;
									
									
								}
								else if((null != this.privateChannel) && receivedInvite){
									synchronized(lockPrivate){
										privateChannel = ssc.accept();	
										lockPrivate.notify();
										Readers.readAddress(generalChannel);
										
									}
								}
								
		
								
								//case we are already connected, destName is not null
								//TODO remove after change RFC with name in packet 7
								else if(null != this.privateChannel && (this.destName != null)){
									System.out.println("Erreur connexion déjà établie avec " + destName);
									Readers.readAddress(generalChannel);
								}
								break;
							case 8:
								name = Readers.readString(generalChannel);
								System.out.println("destName "+ destName +" =? name "+name );
								if(!name.equals(destName))
									System.out.println("Refus d'une ancienne demande (" + name + ").");
								else{
									this.destName = null;
									System.out.println("Votre demande a été refusé.");
								}
								break;
							case 15 : Readers.readMessage(generalChannel);break;
						}
					}catch (IOException e){
						System.err.println("Deconnexion du server.");
						silentlyCloseClient();
					}	

				}
		
		});
		generalListener.start();
		
		this.privateListener = new Thread( () -> {
			
				while(!Thread.interrupted()){
					try{
						//The thread wait until a connection is make.
						synchronized(lockPrivate){
							while(privateChannel == null)
								lockPrivate.wait();
						}
						byte id = Readers.readByte(privateChannel);
						switch(id){
							
						//Exchange address and port between the two clients
						//Here we are c2, we open the channel and connect then send our address and port.
							case 9 : 
								System.out.println("Demande fichier reçu");
								fileChannel = SocketChannel.open();
								fileChannel.connect(Readers.readAddress(privateChannel));
								System.out.println("Connexion pour envoi de fichier établie !");
								currentChannel = privateChannel;
								System.out.println("Vous êtes maintenant sur le chat privé, tapez /g pour revenir sur le chat normal.");
								currentChannel = privateChannel;
								break;
							//TODO remove because useless
							case 10 :
								fileChannel.connect(Readers.readAddress(privateChannel));
								currentChannel = privateChannel;
								System.out.println("Vous êtes maintenant sur le chat privé, tapez /g pour revenir sur le chat normal.");
								break;
							
							//case we have a demand for file.
							case 11:
								synchronized(lock){
									receivedFile = true;
								}
								this.fileReceived = Readers.readDemand(privateChannel);
								System.out.println(destName + " veut vous envoyer " + this.fileReceived);
								System.out.println("Tapez /yes pour accepter ou /no pour refuser.");
								break;
							//case the person has accepted our demand
							case 12:
								synchronized(lockWriteFile){
									acceptFile = true;
									lockWriteFile.notify();
								}
								System.out.println("Demande accepté, envoi en cours...");
								//fileWritter.start();
	;
								break;
							//case the person has refused our demand
							case 13:
								fileSending = false;
								System.out.println("Votre demande d'envoi de fichier a été refusé.");
								break;
							case 15 : Readers.readPrivateMessage(privateChannel);break;
						}
					}catch (IOException e){
						System.err.println("Deconnexion du chat privé.");
						silentlyClosePrivate();
						this.currentChannel = this.generalChannel;
						this.fileChannel = null;
					}
					catch(InterruptedException ie){
						System.err.println("Stop listening on privateChannel");
						Thread.currentThread().interrupt();
					}

				}
			
		});
		privateListener.start();
		
		this.fileListener = new Thread(()-> {
			
				while(!Thread.interrupted()){
					try{
						//Wait until a connection is make
						synchronized(lockReadFile){
							while(fileChannel == null || !receivedFile)
								lockReadFile.wait();
						}
						Readers.readFile(fileChannel,fileReceived);
						//Share with general Thread and main, so use simple lock
						synchronized(lock){
							receivedFile = false;
						}
						System.out.println("Fichier reçu");
					}catch(IOException e){
						System.err.println("Deconnexion pour l'envoi de fichier.");
						silentlyClosePrivate();
						this.currentChannel = this.generalChannel;
					}catch(InterruptedException ie){
						System.err.println("Stop listening on fileChannel.");
						Thread.currentThread().interrupt();
					}	
				}
				
						
		});
		fileListener.start();
		
		this.fileWritter = new Thread(()-> {
			
				while(!Thread.interrupted()){
					try{
						synchronized(lockWriteFile){
							while(!fileSending || !acceptFile)
								lockWriteFile.wait();
						}
								
						Writters.sendFile(fileChannel, Paths.get(fileToSend));
						synchronized(lockWriteFile){
							fileSending = false;
						}
						System.out.println("Envoi terminé");
					}catch(IOException e){
						System.out.println("Connection fermé par le destinataire.");
						System.out.println("Déconnection du chat privé.");
					}catch(InterruptedException ie){
						ie.printStackTrace();
						Thread.currentThread().interrupt();
					}	
				}


				
			
		});
		fileWritter.start();
	}
	
	
	private String askName() throws IOException{


		String name;

			while (true) {
				System.out.println("What is your pseudo ?");
				if (sc.hasNextLine()) {		
					name = sc.nextLine();
					Writters.requestName(generalChannel, name);
					if(Readers.nameAccepted(generalChannel)){

						return name;
					}
					
				}

			}

	}

	private void treatCommand(String line) throws IOException{
		String command;
		if (line.startsWith("/")) {
			command = line.split(" ", 2)[0];
			switch (command) {
			
			//See people connected
			case "/log":
				System.out.println("Voici les personnes connectés :");
				//printPeople();
				return;
				
			//Invite someone	
			case "/invite":
				//Take the two first word
				//Example /invite Bob
				String[] name = line.split(" ", 3);
				
				if(name.length >= 2){
					System.out.println("Demande de chat privé à " + name[1]);
					this.destName = name[1];
					Writters.askPrivateConnection(generalChannel,clientID,myName,name[1]);
				}
				else
					System.out.println("Précisez la personne à inviter !");
				return;
				
			
			//Send file	
			case "/send":
			String[] fileName = line.split(" ", 3);
			if(fileName.length >= 2){
				
				//If we are not already sending a file
				if(!fileSending){
					//Test if the file exist before asking to send
					if(Paths.get(fileName[1]).toFile().exists()){
						synchronized(lockWriteFile){
							fileSending = true;
						}
						fileToSend = fileName[1];
						Writters.askToSendFile(privateChannel,fileName[1]);
						System.out.println("Demande d'envoi du fichier " + fileName[1] + " en cours...");
					}
					else{
						System.out.println("Le fichier " + fileName[1] + " n'existe pas ! Demande annulé.");
					}
				}
				else{
					System.out.println("Le fichier " + fileToSend + " est en attente d'une réponde de " + destName);
				}
			}
			else
				System.out.println("Précisez un fichier à envoyer !");
			return;
			
			
			// Leave private chat
			case "/quit":
				if(privateChannel != null){
					System.out.println("Vous avez quitté le chat privé.");
					silentlyClosePrivate();
					currentChannel = generalChannel;
				}
				else
					System.out.println("Vous n'avez pas de discussion privé en cours.");
				return;
				
			case "/leave":
				silentlyCloseClient();
				return;
				
			
			//accept an invite
			case "/yes":
				synchronized(lock){
					//In this case we are c2, and accept the connection to c1.
					if(receivedInvite && (privateChannel == null)){
						//We prepare the channel here and send our address and port to c1.
						//We need to connect to c1 after, the tread generalListener will do this.
	
						privateChannel = SocketChannel.open();
						Writters.acceptPrivateConnection(generalChannel,clientID,destName,ssc,myName);
						//Let c1 connect to us
						//TODO remove when done
						/*synchronized(lockPrivate){
							privateChannel = ssc.accept();
							lockPrivate.notify();
							currentChannel = privateChannel;
						}*/
						System.out.println("Vous avez accepté l'invitation");
						//privateListener.start();

						
						
					}
					else if(receivedFile && (privateChannel != null) ){
						Writters.acceptFile(privateChannel);
						System.out.println("Vous avez accepté le fichier.");
						//Start the thread to receive file, the tread stop after reading.
						//fileListener.start();
						synchronized(lockReadFile){
							lockReadFile.notify();
						}
					}
					else
						System.out.println("Vous n'avez pas reçu d'invitation");
				}
				return;
			
			//Deny an invite	
			case "/no":
				synchronized(lock){
					if(receivedInvite && (privateChannel == null) ){
						Writters.denyPrivateConnection(generalChannel,clientID,destName);
						receivedInvite = false;
						System.out.println("Vous avez refusé l'invitation.");
					}
					else if(receivedFile && (privateChannel != null)){
						Writters.refuseFile(privateChannel);
						receivedFile = false;
						System.out.println("Vous avez refusé le fichier.");
					}
					else
						System.out.println("Vous n'avez pas reçu d'invitation.");
				}
				return;
			
			//switch to private message
			case "/p" :
				if(currentChannel == privateChannel){
					System.out.println("Vous êtes déjà sur le chat privé.");
				}
				else if(privateChannel != null){
					currentChannel = privateChannel;
					System.out.println("Vous avez basculé sur le chat privé.");
				}
				else
					System.out.println("Pas de chat privé.");
				return;
				
				
			case "/g" :
				//We compare reference
				if(currentChannel == generalChannel){
					System.out.println("Vous êtes déjà sur le chat général.");
				}
				else{
					currentChannel = generalChannel;
					System.out.println("Vous avez basculé sur le chat général.");
				}
				return;
			case "/help" :
				printCommand();
				return;
			
			default :

				break;
					
			}
			
		}
		
		if(currentChannel != privateChannel)
			Writters.sendMessage(generalChannel, clientID, myName, line);
		else
			Writters.sendPrivateMessage(currentChannel, myName, line);
		
		System.out.println(line + " envoyé !");

	}
	
	private void printCommand(){
		System.out.println("Voici la liste des commandes :\n");
		System.out.println("/log : Pas encore disponible.");
		System.out.println("/invite name : demande une connexion privé à \"name\".");
		System.out.println("/leave : quitter le client.");
		System.out.println("/send file : envoi le fichier \"file\" à la personne connecté en privé.");
		System.out.println("/quit : quitter le chat privé.");
		System.out.println("/yes : accepter une demande.");
		System.out.println("/no : refuser une demande.");
		System.out.println("/p : basculer sur le chat privé.");
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
		try{
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
	
	private void silentlyCloseClient(){
		silentlyClose(generalChannel);
		silentlyClosePrivate();
		System.out.println("Le client va fermé.");
		System.exit(0);
		
	}
	
	private void silentlyClosePrivate(){
		silentlyClose(privateChannel);
		silentlyClose(fileChannel);
		this.destName = null;
		this.privateChannel = null;
		this.fileChannel = null;
		System.out.println("Connection privé fermé.");
		
	}

	private void silentlyClose(SocketChannel socket) {
		if (socket != null)
			try {
				socket.close();
			} catch (IOException e) {
				// Ignore
			}

	}

	public static void main(String[] args) throws NumberFormatException,
			UnknownHostException, IOException, InterruptedException {
		new ClientTCPMatou(args[0], Integer.parseInt(args[1])).launch();
	}
}
