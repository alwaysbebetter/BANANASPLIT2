package fr.upem.net.tcp.tp11;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

import fr.upem.net.tcp.protocol.Readers;
import fr.upem.net.tcp.protocol.Writters;


public class Bite {

	// General message come here
	private final SocketChannel generalChannel;

	private Scanner sc;

	private Thread generalListener;

	/**
	 * 
	 * @param serverAdress
	 * @param serverPort
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public Bite(String serverAdress, int serverPort)
			throws UnknownHostException, IOException {
		generalChannel = SocketChannel.open();
		generalChannel.connect(new InetSocketAddress(serverAdress, serverPort));
		this.sc = new Scanner(System.in);
		initListener();
	}

	private void initListener() {
		this.generalListener = new Thread(() -> {
			try {
				while (true) {
					Readers.readSimpleMessage(generalChannel);
				}
			} catch (IOException e) {

			}
		});
		generalListener.start();
	}

	/**
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void launch() throws IOException, InterruptedException {

		System.out.println("Client is ready.");
		String line;
		try {
			while (true) {

				if (sc.hasNextLine()) {
					// First Read message

					line = sc.nextLine();
					Writters.sendSimpleMessage(generalChannel, line);

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
		new Bite(args[0], Integer.parseInt(args[1])).launch();
	}
}