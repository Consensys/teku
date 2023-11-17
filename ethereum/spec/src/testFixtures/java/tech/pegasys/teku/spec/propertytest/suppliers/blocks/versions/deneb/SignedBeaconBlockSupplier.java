package tech.pegasys.teku.spec.propertytest.suppliers.blocks.versions.deneb;

import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.propertytest.suppliers.DataStructureUtilSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SignedBeaconBlockSupplier extends DataStructureUtilSupplier<SignedBeaconBlock> {

  public SignedBeaconBlockSupplier() {
    super(DataStructureUtil::randomSignedBeaconBlock, SpecMilestone.DENEB);
  }
}
