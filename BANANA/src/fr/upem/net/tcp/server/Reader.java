package fr.upem.net.tcp.server;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import javax.xml.crypto.Data;

public interface Reader {
	public static final Charset UTF_8 = Charset.forName("utf-8");
	public static final int BUFSIZ = 200 ;
	public final static int SRC_DATA= 0, DEST_DATA= 1,DEST_DATA_ADR=2; 
	public final static int MAX_VALUE_PORT =  65535, MIN_VALUE_PORT = 1025 ;

	public enum StatusProcessing{
		REFILL,ERROR,DONE
	}
	
	
	public enum StatusReaderTreatment {
			BEGIN,READER_USED,SIZE_STRING_KNOWN
	}

	public StatusProcessing process( ByteBuffer in );	
	public DataPacketRead get();
	public void reset();

	
}
