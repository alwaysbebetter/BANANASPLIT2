package fr.upem.net.tcp.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.util.Scanner;

import fr.upem.net.tcp.protocol.Readers;
import fr.upem.net.tcp.protocol.Writters;

public class ClientTCPTest2 {

	private final SocketChannel generalChannel;
	private String address = "::";
	private int port = 4001;

	public ClientTCPTest2() throws UnknownHostException, IOException {
		generalChannel = SocketChannel.open();
		generalChannel.bind(new InetSocketAddress(address, port));
		System.out.println("bind to "
				+ generalChannel.socket().getLocalSocketAddress());
	}

	public void launch() throws IOException, InterruptedException {

		System.out.println("Client is ready.");


			generalChannel.connect(new InetSocketAddress("::", 4000));
			

	}

	public static void main(String[] args) throws NumberFormatException,
			UnknownHostException, IOException, InterruptedException {
		new ClientTCPTest2().launch();
	}
}