package tech.pegasys.teku.spec.propertytest.suppliers.blocks;

import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.propertytest.suppliers.DataStructureUtilSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SignedBlindedBeaconBlockSupplier extends DataStructureUtilSupplier<SignedBeaconBlock> {

  public SignedBlindedBeaconBlockSupplier() {
    super(DataStructureUtil::randomSignedBlindedBeaconBlock, SpecMilestone.BELLATRIX);
  }
}
