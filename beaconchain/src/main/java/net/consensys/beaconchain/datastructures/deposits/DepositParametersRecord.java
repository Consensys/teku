package net.consensys.beaconchain.datastructures.deposits;

import net.consensys.beaconchain.ethereum.core.Hash;

public class DepositParametersRecord {

  private Int384 pubkey;
  private Int384[] proof_of_possession;
  private Hash withdrawal_credentials;
  private Hash randao_commitment;

  public DepositParametersRecord() {

  }
}
