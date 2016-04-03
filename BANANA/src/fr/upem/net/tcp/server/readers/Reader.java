package fr.upem.net.tcp.server.readers;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import javax.xml.crypto.Data;

import fr.upem.net.tcp.server.DataPacketRead;

public interface Reader {
	public static final Charset UTF_8 = Charset.forName("utf-8");
	public static final int BUFSIZ = 1024 ;
	public final static int SRC_DATA= 0, DEST_DATA= 1,DEST_DATA_ADR=2; 


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
