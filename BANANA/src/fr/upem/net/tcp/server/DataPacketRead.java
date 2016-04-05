package fr.upem.net.tcp.server;

import fr.upem.net.tcp.server.ServerMultiChatTCPNonBlockingWithQueueGoToMatou3.TypePacket;

public class DataPacketRead {
	private TypePacket typePacket = TypePacket.INITIAL_TYPE;
	private int sizeLoginSrc = -1;
	private int sizeLoginDst = -1;
	private int sizeAdressSrc = -1;

	int getSizeAdressDst() {
		return sizeAdressSrc;
	}

	void setSizeAdressDst(int sizeAdressDst) {
		this.sizeAdressSrc = sizeAdressDst;
	}

	String getAdrSrc() {
		return adrSrc;
	}

	void setAdrDest(String adrDest) {
		this.adrSrc = adrDest;
	}

	int getPortSrc() {
		return portSrc;
	}

	void setPortDst(int portDst) {
		this.portSrc = portDst;
	}

	long getId() {
		return id;
	}

	private String loginSrc;
	private String loginDst;
	private String adrSrc;
	private int portSrc;
	private long id = -1;

	public DataPacketRead() {

	}

	TypePacket getTypePacket() {
		return typePacket;
	}

	void setTypePacket(TypePacket typePacket) {
		this.typePacket = typePacket;
	}

	int getSizeLoginSrc() {
		return sizeLoginSrc;
	}

	void setSizeLoginSrc(int sizeLoginSrc) {
		this.sizeLoginSrc = sizeLoginSrc;
	}

	int getSizeLoginDst() {
		return sizeLoginDst;
	}

	void setSizeLoginDst(int sizeLoginDst) {
		this.sizeLoginDst = sizeLoginDst;
	}

	String getLoginSrc() {
		return loginSrc;
	}

	void setLoginSrc(String loginSrc) {
		this.loginSrc = loginSrc;
	}

	String getLoginDst() {
		return loginDst;
	}

	void setLoginDst(String loginDst) {
		this.loginDst = loginDst;
	}

	void setId(long id) {
		this.id = id;
	}

	void reset(){
		 loginSrc =null;
		 loginDst=null;
		 adrSrc=null;
		 portSrc=-1;
		 id = -1;
		 sizeLoginSrc = -1;
		 sizeLoginDst = -1;
		 sizeAdressSrc = -1;
	}

	@Override
	public String toString() {
		return "\n\ttypePacket = " + typePacket + "\n\tsizeLoginSrc  = "
				+ sizeLoginSrc + "\n\tloginSrc = " + loginSrc + "\n\tid = "
				+ id + "\n\tsizeLoginDst = " + sizeLoginDst
				+ "\n\tloginDst  = " + loginDst + "\n\tadrSrc = " + adrSrc
				+ "\n\tportSrc = " + portSrc + "";

	}

}