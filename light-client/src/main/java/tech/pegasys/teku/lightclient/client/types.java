package tech.pegasys.teku.lightclient.client;

import tech.pegasys.teku.bls.impl.PublicKey;

public class types {
	
	private PublicKey pubKey;
	
	public PublicKey getPubkey() {
		return pubKey;
	}

	public void setPubkey(PublicKey pubKey) {
		this.pubKey = pubKey;
	}
	
	public void SyncCommittee() {
		this.setPubkey(pubKey);
	}

}
