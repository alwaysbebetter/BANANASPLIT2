package fr.upem.net.tcp.tp11;


public class Test {

	
	public static void f(String s ){
		s="coucou";
	}
	public static void main(String[] args) {
		
		String s ="c";
		f(s);
		System.out.println(s);
	/*	ByteBuffer bb = ByteBuffer.allocate(10);
		bb.put(Charset.forName("ASCII").encode("cicicaca"));
		bb.flip();
		bb.get();
		bb.get();
		bb.limit(bb.position()+4);
		System.out.println(Charset.forName("ASCII").decode(bb));
		*/
	}
}
