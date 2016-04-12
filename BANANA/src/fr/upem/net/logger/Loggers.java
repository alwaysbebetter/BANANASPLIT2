package fr.upem.net.logger;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class Loggers {
	
	private static final Charset utf8 = Charset.forName("utf-8");
	

	
	public static void test(ByteBuffer buff){
		//TODO
		//Logger pour le paquet 14, paquet avec le fichier
		if(buff == null){
			System.out.println("buff null");
		}
		StringBuilder sb = new StringBuilder();
		buff.flip();
		byte id = buff.get();
		int size,limit;
		sb.append(id).append(" ");
		switch(id){
		case 6:
			size = buff.getInt();
			sb.append(size).append(" ");
			
			limit = buff.limit();
			buff.limit(buff.position() + size);
			sb.append(utf8.decode(buff)).append(" ");
			buff.limit(limit);
			sb.append(buff.getLong());
			System.out.println(sb);
			return;
		case 0:
		case 4:	
			size = buff.getInt();
			sb.append(size).append(" ");
			sb.append(utf8.decode(buff));
			System.out.println(sb);
			return;
		case 11 :

			sb.append(buff.getLong()).append(" ");
			
			size = buff.getInt();
			sb.append(size).append(" ");
			sb.append(utf8.decode(buff));
			System.out.println(sb);
			return;
		case 1:sb.append( buff.getLong()).append(" ");
		case 2:
		case 8:
		case 12:
		case 13 : System.out.println(sb);return;
		case 3: 

		
		size = buff.getInt();
		sb.append(size).append(" ");
		//Get the first string (name)
		limit = buff.limit();
		buff.limit(buff.position() + size );
		sb.append(utf8.decode(buff)).append(" ");
		buff.limit(limit);
		sb.append(buff.getLong() + " ");

		//Get the second String (message)
		size = buff.getInt();

		sb.append(size).append(" ").append(utf8.decode(buff));
		System.out.println(sb);return;

		case 15://Message Client to client and server to client only
			size = buff.getInt();
			sb.append(size).append(" ");
			//Get the first string (name)
			limit = buff.limit();
			buff.limit(buff.position() + size );
			sb.append(utf8.decode(buff)).append(" ");
			buff.limit(limit);

			//Get the second String (message)
			size = buff.getInt();

			sb.append(size).append(" ").append(utf8.decode(buff));
			System.out.println(sb);
			return;
		case 5 :
			size = buff.getInt();
			sb.append(size).append(" ");
			
			//Get the first string
			limit = buff.limit();
			buff.limit(buff.position() + size);
			sb.append(utf8.decode(buff)).append(" ");
			buff.limit(limit);
			
			sb.append(buff.getLong()).append(" ");
			
			//Get the second String
			size = buff.getInt();
			limit = buff.limit();
			buff.limit(buff.position() + size);
			sb.append(size).append(" ").append(utf8.decode(buff)).append(" ");
			buff.limit(limit);
			sb.append(buff.getInt());
			
			System.out.println(sb);
			return;
		case 7:
		case 9 :
		case 10:
			size = buff.getInt();
			sb.append(size).append(" ");
			
			limit = buff.limit();
			buff.limit(buff.position() + size);
			sb.append(utf8.decode(buff)).append(" ");
			buff.limit(limit);
			sb.append(buff.getInt());
			System.out.println(sb);
			return;
		case 14 :
			long length;
			length = buff.getLong();
			sb.append(length).append(" ");
			//We don't print all the byte, but we check if the number of byte is respected
			//It is the what we want to know
			if(buff.remaining() != length)
				sb.append("error, maybe data is missing");
			else
				sb.append("data is ok");
			//Set the position to the end like we had read it.
			buff.position(buff.limit());
			System.out.println(sb);
			return;
		default :
			System.out.println("Erreur id. : "+id);
			return;
		}
		
	}

	public static void testChatMessage(ByteBuffer buff){
		StringBuilder sb = new StringBuilder();
		buff.flip();
		byte id = buff.get();
		int size,limit;
		sb.append(id).append(" ");
		
		size = buff.getInt();
		sb.append(size).append(" ");
		//Get the first string (name)
		limit = buff.limit();
		buff.limit(buff.position() + size );
		sb.append(utf8.decode(buff)).append(" ");
		buff.limit(limit);
		sb.append(buff.getLong() + " ");

		//Get the second String (message)
		size = buff.getInt();

		sb.append(size).append(" ").append(utf8.decode(buff));
		System.out.println(sb);
	}
	
	

}
