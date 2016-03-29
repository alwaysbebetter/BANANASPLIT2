package fr.upem.net.tcp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Set;

public class ServerTCPMatou {
	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Set<SelectionKey> selectedKeys;
	private final HashMap<String,SocketChannel> map = new HashMap<>();

	public ServerTCPMatou(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
		selectedKeys = selector.selectedKeys();
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		Set<SelectionKey> selectedKeys = selector.selectedKeys();
		while (!Thread.interrupted()) {
			selector.select();
			processSelectedKeys();
			selectedKeys.clear();
		}
	}

	private void processSelectedKeys() throws IOException {
		for (SelectionKey key : selectedKeys) {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
			if (key.isValid() && key.isWritable()) {
				doWrite(key);
			}
			if (key.isValid() && key.isReadable()) {
				doRead(key);
			}
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		SocketChannel sc = serverSocketChannel.accept();
		if(sc == null) {
			return ;
		}
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ, new Data());
	}

	private void doRead(SelectionKey key) throws IOException {
		/*ByteBuffer buff = (ByteBuffer)key.attachment();
		SocketChannel client = (SocketChannel)key.channel();
		client.read(buff);
		if(buff.remaining() != 0) {
			return;
		}
		buff.flip();
		int operand1 = buff.getInt(), operand2 = buff.getInt();
		System.out.println(operand1+" "+operand2);
		int sum = operand1 + operand2;
		buff.clear();
		buff.putInt(sum);
		buff.flip();*/
		Data data = (Data)key.attachment();
		SocketChannel client = (SocketChannel)key.channel();
		
		if(!data.read(key))
			return;
		
		key.interestOps(SelectionKey.OP_WRITE);
	}

	private void doWrite(SelectionKey key) throws IOException {
		SocketChannel client = (SocketChannel)key.channel();
		ByteBuffer buff = (ByteBuffer)key.attachment();
		client.write(buff);
		if(buff.remaining() != 0) {
			return ;
		}
		buff.clear();
		key.interestOps(SelectionKey.OP_READ);
	}
	
	private void sendAll(ByteBuffer message){
		//TODO
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		new ServerTCPMatou(Integer.parseInt(args[0])).launch();
	}
	
}
