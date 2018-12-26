package tech.pegasys.artemis.datastructures.beaconchainoperations;

public class AttestationDataAndCustodyBit {

  private AttestationData data;
  private boolean poc_bit;

  AttestationDataAndCustodyBit() {

  }

  public boolean isPoc_bit() {
    return poc_bit;
  }

  public void setPoc_bit(boolean poc_bit) {
    this.poc_bit = poc_bit;
  }

  public AttestationData getData() {
    return data;
  }

  public void setData(AttestationData data) {
    this.data = data;
  }
}