package fr.upem.net.logger;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class Logger {
	
	private ByteBuffer buff;
	private final Charset utf8 = Charset.forName("utf-8");
	
	/**
	 * Construct a Logger to verify a Matou request.
	 * @param buff The buff send in the request to test.
	 */
	public Logger(ByteBuffer buff){
		this.buff = buff;
	}
	
	private String resultTest(){
		StringBuilder sb = new StringBuilder();
		buff.flip();
		int id = buff.getInt();
		int size,limit;
		sb.append(id).append(" ");
		switch(id){
		case 0:
		case 4:
		case 6:
		case 11 :
			size = buff.getInt();
			sb.append(size).append(" ");
			buff.compact();
			sb.append(utf8.decode(buff));
			return sb.toString();
		case 1:
		case 2:
		case 8:
		case 12:
		case 13 : return sb.toString();
		case 3:
		case 15:
			size = buff.getInt();
			sb.append(size).append(" ");
			//Get the first string
			buff.compact();
			limit = buff.limit();
			buff.limit(size);
			sb.append(utf8.decode(buff));
			buff.limit(limit);
			//Get the second String
			size = buff.getInt();
			buff.compact();
			sb.append(size).append(" ").append(utf8.decode(buff));
			return sb.toString();
		case 5 :
			size = buff.getInt();
			sb.append(size).append(" ");
			//Get the first string
			buff.compact();
			limit = buff.limit();
			buff.limit(size);
			sb.append(utf8.decode(buff));
			buff.limit(limit);
			//Get the second String
			size = buff.getInt();
			buff.compact();
			limit = buff.limit();
			buff.limit(size);
			sb.append(size).append(" ").append(utf8.decode(buff));
			buff.limit(limit);
			buff.compact();
			sb.append(buff.getInt());
			return sb.toString();
		case 7:
		case 9 :
		case 10:
			size = buff.getInt();
			sb.append(size).append(" ");
			buff.compact();
			
			limit = buff.limit();
			buff.limit(size);
			sb.append(utf8.decode(buff));
			buff.limit(limit);
			sb.append(buff.getInt());
			return sb.toString();
			
		
		default :
			return null;
		}
		
	}

	@Override
	public String toString() {
		return resultTest();
	}
	
	

}
