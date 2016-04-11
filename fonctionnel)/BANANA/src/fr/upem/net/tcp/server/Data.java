package fr.upem.net.tcp.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

class Data {
	ByteBuffer bCode = ByteBuffer.allocateDirect(Integer.BYTES);
	ByteBuffer bSize = ByteBuffer.allocateDirect(Integer.BYTES);
	ByteBuffer bName = null;

	/** Read all data for a request. Depends of code.
	 * 
	 * @param key
	 * @return
	 * @throws IOException
	 */
	public boolean read(SelectionKey key) throws IOException{
		SocketChannel client = (SocketChannel)key.channel();
		// Read code of protocol
		client.read(bCode);
		if (bCode.remaining() != 0) {
			return false;
		}
		bCode.flip();
		int code = bCode.getInt();
		switch (code) {
		case 0:
			return readZero(key);
		}
		return false;
	}

	/** Read all data for type of request 0. return true if all data was read
	 * 
	 * @param key
	 * @return
	 * @throws IOException
	 */
	public boolean readZero(SelectionKey key) throws IOException{
		SocketChannel client = (SocketChannel)key.channel();
		
		// Read size of name
		client.read(bSize);
		if (bSize.remaining() != 0) {
			return false;
		}
		//Allcate buffer for name
		if(bName == null){
			bSize.flip();
			bName = ByteBuffer.allocate(bSize.getInt());
		}
		
		//Read name
		client.read(bName);
		if (bName.remaining() != 0) {
			return false;
		}
		
		// Here we have read all data needed
		return true;
		
	}
	public boolean treat(SelectionKey key){
		//TODO
		
		return false;
	}
	
	
	
	public boolean treatZero(SelectionKey key){
		//TODO
		return false;
	}

}