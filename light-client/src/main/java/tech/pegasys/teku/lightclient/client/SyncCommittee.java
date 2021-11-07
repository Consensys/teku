package tech.pegasys.teku.lightclient.client;

import tech.pegasys.teku.bls.impl.PublicKey;

public class SyncCommittee {
	
	  private PublicKey pubKey;

	  public PublicKey getPubkey() {
	    return pubKey;
	  }

	  public void setPubkey(PublicKey pubKey) {
	    this.pubKey = pubKey;
	  }	

}
