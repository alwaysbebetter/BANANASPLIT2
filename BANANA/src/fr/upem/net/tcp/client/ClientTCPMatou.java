package fr.upem.net.tcp.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.util.Scanner;


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
	
	//List of folks connected
	//We use a Linked list because people will connect and disconnect(add/remove) often
	//private final LinkedList<String> listPeople = new LinkedList<>();
	
	private final String myName;
	private String destName;
	private String fileToSend,fileReceived;
	private Scanner sc ;
	//To prevent identity stealing.
	private final long clientID;
	// If we have been invite by someone
	private  boolean receivedInvite = false;
	private  boolean receivedFile = false,fileSending = false;
	
	private final Object lock = new Object();
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
		generalChannel.connect(new InetSocketAddress(serverAdress, serverPort));
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
			try{
				while(!Thread.interrupted()){
					byte id = Readers.readByte(generalChannel);
					switch(id){
						
						case 4 : 
							synchronized(lock){
								this.receivedInvite = true;
							}
							this.destName = Readers.readDemand(generalChannel);
							System.out.println(destName + " has invited you.");
							System.out.println("Tape /yes to accept or /no to refuse.");
							
							break;
							
						case 7 :

							//In this case we are c1 because we are not yet connected like c2.
							//c2 has received our demand so his privateChannel is open.
							if(null == this.privateChannel){
								

								System.out.println("Votre demande a été accepté par " + destName +" !");
								//Here we receive the server address of c2 so we can connect to him.
								privateChannel = SocketChannel.open(Readers.readAddress(generalChannel));
								privateListener.start();
								Writters.askPrivateFileConnection(privateChannel,(byte)9,ssc);
								System.out.println("Demande de connexion pour envoyer des fichiers...");
								fileChannel = ssc.accept();
								System.out.println("Connexion pour envoi de fichier établie !");
								
							}
							//In this case we are c2 because we already had accept and open a channel for c1.
							//We just have to connect to c1
							else{
								System.out.println("Erreur connexion déjà établie avec " + destName);
							}
							break;
						case 15 : Readers.readMessage(generalChannel);break;
					}

				}
			}catch (IOException e){
				e.printStackTrace();
			}			
		});
		generalListener.start();
		
		this.privateListener = new Thread( () -> {
			try{
				while(!Thread.interrupted()){
					byte id = Readers.readByte(privateChannel);
					switch(id){
						
					//Exchange address and port between the two clients
					//Here we are c2, we open the channel and connect then send our address and port.
						case 9 : 
							fileChannel = SocketChannel.open(Readers.readAddress(privateChannel));
							System.out.println("Connexion pour envoi de fichier établie !");
							break;
						//TODO remove because useless
						case 10 :
							fileChannel.connect(Readers.readAddress(privateChannel));
							break;
						
						//case we have a demand for file.
						case 11:
							//TODO
							synchronized(lock){
								receivedFile = true;
							}
							this.fileReceived = Readers.readDemand(privateChannel);
							System.out.println(destName + " wants to send you " + this.fileReceived);
							System.out.println("Tape /yes to accept or /no to refuse.");
							break;
						//case the person has accepted our demand
						case 12:
							System.out.println("Demande accepté, envoi en cours...");
							fileWritter.start();break;
						//case the person has refused our demand
						case 13: fileSending = false;break;
						case 15 : Readers.readMessage(privateChannel);break;
					}

				}
			}catch (IOException e){
				
			}			
		});
		
		this.fileListener = new Thread(()-> {
			try{
					Readers.readFile(fileChannel,fileReceived);
					receivedFile = false;
					System.out.println("Fichier reçu");
					return;
				
			}catch(IOException e){
				e.printStackTrace();
			}
			
			
		});
		this.fileWritter = new Thread(()-> {
			try{
					Writters.sendFile(fileChannel, Paths.get(fileToSend));
					fileSending = false;
					System.out.println("Envoi terminé");
					return;
				
			}catch(IOException e){
				e.printStackTrace();
			}
			
			
		});
	}
	
	/* Enlever les condition commenter aprés test */
	private String askName() throws IOException{


		String name;

			while (true) {
				System.out.println("What is your pseudo ?");
				if (sc.hasNextLine()) {
					// Ask name				
					name = sc.nextLine();
					Writters.requestName(generalChannel, name);
					//TODO
					//if à commenter pour tester le client sans serveur
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
						fileSending = true;
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
					silentlyClose(privateChannel);
					silentlyClose(fileChannel);
					privateChannel = null;
					fileChannel = null;
					currentChannel = generalChannel;
				}
				else
					System.out.println("Vous n'avez pas de discussion privé en cours.");
				return;
			
			//accept an invite
			case "/yes":
				//In this case we are c2, and accept the connection to c1.
				if(receivedInvite && (privateChannel == null)){
					//We prepare the channel here and send our address and port to c1.
					//We need to connect to c1 after, the tread generalListener will do this.

					Writters.acceptPrivateConnection(generalChannel,clientID,destName,ssc);
					//Let c1 connect to us
					privateChannel = ssc.accept();
					System.out.println("Vous avez accepté l'invitation");
					privateListener.start();
				}
				else if(receivedFile && (privateChannel != null) ){
					Writters.acceptFile(privateChannel);
					System.out.println("Vous avez accepté le fichier.");
					//Start the thread to receive file, the tread stop after reading.
					fileListener.start();
				}
				else
					System.out.println("Vous n'avez pas reçu d'invitation");
				return;
			
			//Deny an invite	
			case "/no":
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
				return;
			
			//switch to private message
			case "/p" :
				if(privateChannel != null){
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
			Writters.sendMessage(currentChannel, clientID, myName, line);
		else
			Writters.sendPrivateMessage(currentChannel, myName, line);
		
		System.out.println(line + " envoyé !");

	}
	
	private void printCommand(){
		System.out.println("Voici la liste des commandes :\n");
		System.out.println("/log : Pas encore disponible.");
		System.out.println("/invite name : demande une connexion privé à \"name\".");
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
			silentlyClose(generalChannel);
			sc.close();
		}
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