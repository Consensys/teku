package net.consensys.beaconchain.datastructures;

import net.consensys.beaconchain.util.uint.UInt384;
import net.consensys.beaconchain.util.uint.UInt64;

public class CasperSlashingSpecial {

  private UInt64 slot;
  private UInt64 validator_index;
  private UInt384[] signature;

  public CasperSlashingSpecial() {

  }

}
