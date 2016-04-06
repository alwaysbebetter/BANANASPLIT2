package fr.upem.net.tcp.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;

public class ClientTCPTest {

	private final ServerSocketChannel sc;
	private String address = "::";
	private int port = 4000;
	private Selector selector;

	public ClientTCPTest() throws UnknownHostException, IOException {
		sc = ServerSocketChannel.open();
		sc.bind(new InetSocketAddress(address, port));
		selector = Selector.open();
		System.out.println("bind to "
				+ sc.socket().getLocalSocketAddress());
	}

	public void launch() throws IOException, InterruptedException {

		System.out.println("Client is ready.");

	
			//try (Scanner sca = new Scanner(System.in)) {
			//	if (sca.hasNext()) {
					//sca.next();
	
					SocketChannel sc2= sc.accept();
					System.out.println("accept " + sc2.socket().getLocalSocketAddress());
				

			//	}
		//	}

		//}

	}

	public static void main(String[] args) throws NumberFormatException,
			UnknownHostException, IOException, InterruptedException {
		new ClientTCPTest().launch();
	}
}