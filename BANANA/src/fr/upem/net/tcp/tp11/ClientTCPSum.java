package fr.upem.net.tcp.tp11;



import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

public class ClientTCPSum {
	private final SocketChannel socket;

	public ClientTCPSum(String remoteHost, int remotePort) throws UnknownHostException, IOException {
		socket = SocketChannel.open();
		socket.connect(new InetSocketAddress(remoteHost, remotePort));
	}

	public void launch() throws IOException, InterruptedException {
		ByteBuffer bb = ByteBuffer.allocate(4);
		Scanner sc = new Scanner(System.in);
		try {
			for(int i=0 ; i<2 ; ++i)  {
				int op = sc.nextInt();
				bb.putInt(op);
				bb.flip();
				bb.limit(3);
				socket.write(bb);
				Thread.sleep(1000);
				bb.limit(4);
				socket.write(bb);
				bb.clear();
			}
			bb.limit(4);
			readFully(bb,socket);
			if (bb.hasRemaining()) {
				System.out.println("Serveur closed the connection before sending 4 bytes");
				return;
			}
			bb.flip();
			int res = bb.getInt();
			System.out.println("resultat = " + res);
			bb.clear();
		} finally {
			silentlyClose(socket);
			sc.close();
		}
	}

	private void silentlyClose(SocketChannel socketChannel) {
		if (socketChannel!=null)
			try {
				socketChannel.close();
			} catch (IOException e) {
               // Ignore
			}
		
	}

	private void readFully(ByteBuffer bb, SocketChannel socketChannel) throws IOException {
		while(bb.hasRemaining() && socketChannel.read(bb)!=-1) {			
		}
	}

	public static void main(String[] args) throws NumberFormatException, UnknownHostException, IOException, InterruptedException {
		new ClientTCPSum(args[0],Integer.parseInt(args[1])).launch();
	}
}


