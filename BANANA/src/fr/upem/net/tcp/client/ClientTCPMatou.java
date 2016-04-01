package fr.upem.net.tcp.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.util.Scanner;

import fr.upem.net.tcp.protocol.Readers;
import fr.upem.net.tcp.protocol.Writters;

public class ClientTCPMatou {
	
	private SocketChannel currentChannel;
	
	//General message come here
	private final SocketChannel generalChannel;
	
	//Private message come here
	private SocketChannel privateChannel = null;
	
	//Channel uses to download/upload file
	private SocketChannel fileChannel = null;
	
	//List of folks connected
	//We use a Linked list because people will connect and disconnect(add/remove) often
	//private final LinkedList<String> listPeople = new LinkedList<>();
	
	private final String myName;
	private String destName = "destTest";
	private String fileName;
	private Scanner sc ;
	//To prevent identity stealing.
	private final long clientID;
	// If we have been invite by someone
	private  boolean receivedInvite = true;
	private  boolean receivedFile = true;
	
	private final Object lock = new Object();
	private Thread generalListener, privateListener;
	
	

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
				while(true){
					int id = Readers.readInt(generalChannel);
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
							//In this case we are c1 because c2 have open a channel for us.
							if(privateChannel == null){
								privateChannel = SocketChannel.open(Readers.readAddress(generalChannel));
								Writters.acceptPrivateConnection(generalChannel,clientID,destName,privateChannel);
								fileChannel = SocketChannel.open();
								Writters.askPrivateFileConnection(privateChannel,9,fileChannel);
							}
							//In this case we are c2 because we already had accept and open a channel for c1.
							//We just have to connect to c1
							else{
								this.privateChannel.connect(Readers.readAddress(generalChannel));
							}
							privateListener.start();
							break;
						case 15 : Readers.readMessage(generalChannel);break;
					}

				}
			}catch (IOException e){
				
			}			
		});
		generalListener.start();
		
		this.privateListener = new Thread( () -> {
			try{
				while(true){
					int id = Readers.readInt(privateChannel);
					switch(id){
						
					//Exchange address and port between the two clients
					//Here we are c2, we open the channel and connect then send our address and port.
						case 9 : 
							fileChannel = SocketChannel.open(Readers.readAddress(privateChannel));
							Writters.askPrivateFileConnection(privateChannel,10,fileChannel);
							break;
						//Here we are c1 and connect to c2
						case 10 :
							fileChannel.connect(Readers.readAddress(privateChannel));
							break;
						
						//case we have a demand for file.
						case 11:
							//TODO
							synchronized(lock){
								receivedFile = true;
							}
							this.fileName = Readers.readDemand(privateChannel);
							System.out.println(destName + " wants to send you " + this.fileName);
							System.out.println("Tape /yes to accept or /no to refuse.");
							break;
						
						case 15 : Readers.readMessage(privateChannel);break;
					}

				}
			}catch (IOException e){
				
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
				System.out.println("Envoi du fichier " + fileName[1] + " en cours...");
				//TODO
				//Create a thread to send the file.
				Writters.sendFile(generalChannel,Paths.get(fileName[1]));
			}
			else
				System.out.println("Précisez un fichier à envoyer !");
			return;
			
			
			// Leave private chat
			case "/quit":
				if(privateChannel != null){
					System.out.println("Vous avez quitté le chat privé.");
					silentlyClose(privateChannel);
					privateChannel = null;
					currentChannel = generalChannel;
				}
				else
					System.out.println("Vous n'avez pas de discussion privé en cours.");
				return;
			
			//accept an invite
			case "/yes":
				//In this case we are c2, and accept the connection to c1.
				if(receivedInvite){
					System.out.println("Vous avez accepté l'invitation");
					//We prepare the channel here and send our address and port to c1.
					//We need to connect to c1 after, the tread generalListener will do this.
					privateChannel = SocketChannel.open();
					Writters.acceptPrivateConnection(generalChannel,clientID,destName,privateChannel);
				}
				if(receivedFile && (privateChannel != null) ){
					System.out.println("Vous avez accepté le fichier.");
					Writters.acceptFile(privateChannel);
				}
				else
					System.out.println("Vous n'avez pas reçu d'invitation");
				return;
			
			//Deny an invite	
			case "/no":
				if(receivedInvite && (privateChannel == null) ){
					System.out.println("Vous avez refusé l'invitation.");
					Writters.denyPrivateConnection(generalChannel,clientID,destName);
				}
				if(receivedFile && (privateChannel != null)){
					System.out.println("Vous avez refusé le fichier.");
					Writters.refuseFile(privateChannel);
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
				System.out.println("Voici la liste des commandes :");
				//printCommand();
				return;
			
			default :

				break;
					
			}
			
		}
		System.out.println(line + " envoyé !");
		Writters.sendMessage(currentChannel, clientID, myName, line);

	}

		
	/**
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void launch() throws IOException, InterruptedException {
		
		System.out.println("Client is ready.");
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