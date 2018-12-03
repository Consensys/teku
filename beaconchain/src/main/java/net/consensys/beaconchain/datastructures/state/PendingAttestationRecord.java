package net.consensys.beaconchain.datastructures.state;

public class PendingAttestationRecord {

  private AttestationData data;
  private Bytes participation_bitfield;
  private Bytes custody_bitfield;
  private Uint64 slot_included;

  public PendingAttestationRecord() {

  }

}
