package net.consensys.beaconchain.datastructures.state;

import net.consensys.beaconchain.ethereum.core.Hash;

public class ValidatorRecord {

  private Uint384 pubkey;
  private Hash withdrawal_credentials;
  private Hash randao_commitment;
  private Uint64 randao_skips;
  private Uint64 balance;
  private Uint64 status;
  private Uint64 last_status_change_slot;
  private Uint64 exit_seq;

  public ValidatorRecord() {

  }

}
