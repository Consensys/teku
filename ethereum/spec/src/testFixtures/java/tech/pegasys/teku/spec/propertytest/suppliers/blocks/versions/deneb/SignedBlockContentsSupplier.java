package tech.pegasys.teku.spec.propertytest.suppliers.blocks.versions.deneb;

import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;
import tech.pegasys.teku.spec.propertytest.suppliers.DataStructureUtilSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SignedBlockContentsSupplier extends DataStructureUtilSupplier<SignedBlockContents> {

  public SignedBlockContentsSupplier() {
    super(DataStructureUtil::randomSignedBlockContents, SpecMilestone.DENEB);
  }
}
