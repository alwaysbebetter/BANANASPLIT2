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
			sb.append(utf8.decode(buff));
			System.out.println(sb);
			return;
		case 1:
		case 2:
		case 8:
		case 12:
		case 13 : System.out.println(sb);
		case 3:
		case 15:
			size = buff.getInt();
			sb.append(size).append(" ");
			//Get the first string
			limit = buff.limit();
			buff.limit(buff.position() + size );
			sb.append(utf8.decode(buff)).append(" ");
			buff.limit(limit);

			//Get the second String
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
			sb.append(utf8.decode(buff));
			buff.limit(limit);
			sb.append(buff.getInt());
			System.out.println(sb);
			return;
		
		default :
			System.out.println("Erreur id.");
			return;
		}
		
	}

	
	

}
