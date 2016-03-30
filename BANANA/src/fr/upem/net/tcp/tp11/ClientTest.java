package fr.upem.net.tcp.tp11;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;


public class ClientTest {

	
	public static void main(String args[]) throws IOException
	{
		final SocketChannel sc=SocketChannel.open();
		try{
		sc.connect(new InetSocketAddress(args[0],Integer.parseInt(args[1])));
		}
		catch (Exception e){
			e.printStackTrace();
			System.out.println("Usage example : ClientTest localhost 7777");
			return;
		}
		
		Runnable writer = new Runnable(){

			@Override
			public void run() {
				try {
					ByteBuffer bb= ByteBuffer.allocate(2);
					bb.put((byte) 1);
					bb.put((byte) 2);
					bb.flip();
				while(true) {
					sc.write(bb);
					bb.flip();
				}
				} catch (IOException e) {
					System.out.println();
					System.err.println("Server is down!");
					System.exit(0);
				}
			}
			
		};
		
		Runnable reader = new Runnable(){

			@Override
			public void run() {
				ByteBuffer buf= ByteBuffer.allocate(1);
				try {
					while(sc.read(buf)!=-1){
						buf.flip();
						System.out.print(buf.get() & 0xFF);
						buf.clear();
						Thread.sleep(100);
					}
				} catch (IOException | InterruptedException e) {
					System.out.println();
					System.err.println("Server is down!");
					System.exit(0);
				}
				System.out.println("Server is done!");
			}
				
			
		};
		new Thread(reader).start();
		new Thread(writer).start();
	}
}
