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
	private  String destName = "destTest";
	
	// If we have been invite by someone
	private  boolean receivedInvite = true;
	private final Object lock = new Object();
	
	

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
		//myName = askName();
		//System.out.println(myName);
		myName = "moi";
		currentChannel = generalChannel;
		initListener();
	}
	
	private void initListener(){
		//TODO
		Thread generalListener = new Thread( () -> {
			try{
				while(true){
					int id = Readers.readInt(generalChannel);
					switch(id){
						
						case 4 : 
							this.receivedInvite = true;
							this.destName = Readers.readDemandConnection(generalChannel);
							break;
							
						case 7 :
							privateChannel = Readers.readAdress(generalChannel);break;
						case 15 : Readers.readMessage(generalChannel);break;
					}

				}
			}catch (IOException e){
				
			}
			
		});
	}
	
	/*Cette méthode bloquer avec le scanner, à corriger pour que le scanner se ferme vraiment */
	private String askName() throws IOException{
		//TODO
		try (Scanner sc = new Scanner(System.in)) {
			while (true) {
				System.out.println("What is your pseudo ?");
				if (sc.hasNextLine()) {
					// Ask name				
					String name = sc.nextLine();
					Writters.requestName(generalChannel, name);
					//if(Readers.nameAccepted(generalChannel)){
						sc.close();
						return name;
					//}
					
				}

			}
		}
	}

	private void treatCommand(String line) throws IOException{
		String command;
		System.out.println("treat");
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
				//Exemple /invite Bob
				String[] name = line.split(" ", 3);
				if(name.length >= 2){
					System.out.println("Demande de chat privé à " + name[1]);
					Writters.askPrivateConnection(generalChannel,myName,name[1]);
				}
				else
					System.out.println("Précisez la personne à inviter !");
				return;
				
			//Send file	
			case "/send":
			String[] fileName = line.split(" ", 3);
			if(fileName.length >= 2){
				System.out.println("Envoi du fichier " + fileName[1] + " en cours...");
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
				if(receivedInvite){
					System.out.println("Vous avez accepté l'invitation");
					Writters.acceptPrivateConnection(generalChannel,destName);
				}
				else
					System.out.println("Vous n'avez pas reçu d'invitation");
				return;
			
			//Deny an invite	
			case "/no":
				if(receivedInvite){
					System.out.println("Vous avez refusé l'invitation.");
					Writters.denyPrivateConnection(generalChannel,destName);
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
		Writters.sendMessage(currentChannel, myName, line);

	}

		
	/**
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void launch() throws IOException, InterruptedException {
		
		System.out.println("Client is ready.");
		String line;
		try (Scanner sc = new Scanner(System.in)) {
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