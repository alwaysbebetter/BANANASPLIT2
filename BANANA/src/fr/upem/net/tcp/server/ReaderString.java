package fr.upem.net.tcp.server;

import java.nio.ByteBuffer;

import fr.upem.net.tcp.server.DataPacketRead;
import fr.upem.net.tcp.server.ServerTCP.TypePacket;

public class ReaderString implements Reader {
	private int sizeString;
	private DataPacketRead data;
	private StatusReaderTreatment status = StatusReaderTreatment.BEGIN;
	private Reader reader;
	private final int concernedData;

	public ReaderString(Reader reader, int concernedData) {
		this.reader = reader;
		this.concernedData = concernedData;// HANDLE TO KNOW IF WE ARE READING
											// LOGIN FROM SRC OR DATA FROM DEST
	}

	public ReaderString(int concernedData, TypePacket typePacket) {
		this.concernedData = concernedData;
		data = new DataPacketRead();
		data.setTypePacket(typePacket);
	}

	/**
	 * treatment:
	 * 
	 * retrieve sizeLogin and login from the {@link ByteBuffer} in and save
	 * those elements in the field "data"
	 * 
	 * @param in
	 */
	private void treatment(ByteBuffer in) {
		in.flip();
		int oldLimit = in.limit();
		in.limit(sizeString);
		switch (concernedData) {
		case SRC_DATA:
			data.setLoginSrc(UTF_8.decode(in).toString());
			break;
		case DEST_DATA:
			data.setLoginDst(UTF_8.decode(in).toString());
			break;
		case DEST_DATA_ADR:
			
			data.setAdrDest(UTF_8.decode(in).toString());
			break;
		}
		in.limit(oldLimit);
		in.compact();
	}

	@Override
	public StatusProcessing process(ByteBuffer in) {

		switch (status) {

		case BEGIN:
			if (reader == null) {
				System.out.println("here");
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
				sizeString = in.getInt();
				System.out.println("ReaderString sizeString = "+sizeString);
				if ((sizeString >= BUFSIZ) || (sizeString < 0)) {// that handle
																	// to check
																	// all size
																	// of login
																	// and of
																	// message.
					System.out.println("sizeString :=" + sizeString);
					return StatusProcessing.ERROR;
				}

				switch (concernedData) {
				case SRC_DATA:
					data.setSizeLoginSrc(sizeString);
					break;
				case DEST_DATA:
					data.setSizeLoginDst(sizeString);
					break;
				case DEST_DATA_ADR:
					data.setSizeAdressDst(sizeString);
					break;
				}

				in.compact();
				status = StatusReaderTreatment.SIZE_STRING_KNOWN;
			}// on envel volontairement le break dans le but de passer a la size
				// suivante
		case SIZE_STRING_KNOWN:
			if (in.position() >= sizeString) {
				treatment(in);
				System.out.println("DATA READ BY ReaderString : "+data);
				status = StatusReaderTreatment.BEGIN;
				return StatusProcessing.DONE;
			}
			break;
		}

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
