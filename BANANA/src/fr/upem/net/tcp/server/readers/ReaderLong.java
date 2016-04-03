package fr.upem.net.tcp.server.readers;

import java.nio.ByteBuffer;

import fr.upem.net.tcp.server.DataPacketRead;

public class ReaderLong implements Reader {

	private StatusReaderTreatment status = StatusReaderTreatment.BEGIN;
	private DataPacketRead data;
	private Reader reader;

	public ReaderLong(Reader reader) {
		this.reader = reader;
	}

	public ReaderLong() {
		data = new DataPacketRead();
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
			if (in.position() >= Long.BYTES) {
				in.flip();
				data.setId(in.getLong());
				in.compact();

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
