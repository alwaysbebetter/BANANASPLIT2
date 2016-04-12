import java.nio.ByteBuffer;
import java.nio.charset.Charset;


public class Test {
	
	public static void main(String[] args){
		Charset charset = Charset.forName("utf-8");
		ByteBuffer buff = charset.encode("abcdefghijklmnopqrstuvwxyz");
		ByteBuffer bb = ByteBuffer.allocate(30);
		bb.put(buff);
		bb.flip();
		System.out.println(bb.remaining());
		bb.position(bb.position() + 26);
		System.out.println(bb.hasRemaining());
		System.out.println(bb.remaining());
		bb.flip();
		System.out.println(bb.remaining());
	}

}
