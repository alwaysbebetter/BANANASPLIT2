package fr.upem.net.tcp.server;


import java.nio.ByteBuffer;

import fr.upem.net.tcp.server.DataPacketRead;
import fr.upem.net.tcp.server.Reader.StatusReaderTreatment;
import fr.upem.net.tcp.server.ServerMultiChatTCPNonBlockingWithQueueGoToMatou3.TypePacket;

public class ReaderInt implements Reader {

	private StatusReaderTreatment status = StatusReaderTreatment.BEGIN;
	private Reader reader;
	private DataPacketRead data ;

	public ReaderInt(Reader reader) {
		this.reader = reader;
	}

	public ReaderInt(TypePacket typePacket) {
		data = new DataPacketRead();
		data.setTypePacket(typePacket);
	}
	

	@Override
	public StatusProcessing process(ByteBuffer in) {

		switch (status) {

		case BEGIN:
			if (reader == null) {
				status = StatusReaderTreatment.READER_USED;
			} else {
				StatusProcessing statusCalledReader = reader.process(in);
				if (statusCalledReader == StatusProcessing.DONE) {
					data = reader.get();

					status = StatusReaderTreatment.READER_USED;
				} else {
					return statusCalledReader;// REFILL OR ERROR
				}
			}
		case READER_USED:
			if (in.position() >= Integer.BYTES) {
				in.flip();
				int theInteger = in.getInt() ;
				if(theInteger <= MIN_VALUE_PORT || theInteger >= MAX_VALUE_PORT ){
					return StatusProcessing.ERROR;
				}
				data.setPortDst(theInteger);
				in.compact();
				System.out.println("PORT READ BY ReaderInt : "+data.getPortSrc());
				status = StatusReaderTreatment.BEGIN;
				return StatusProcessing.DONE;
			}
			break;// on envel volontairement le break dans le but de passer a la
					// size
		} // suivante

		// c quand qu'on a error
		return StatusProcessing.REFILL;
	}

	@Override
	public DataPacketRead get() {

		return data;
	}

	@Override
	public void reset() {
		if (reader != null) {
			reader.reset();
		}
		// clear data ??

	}

}
