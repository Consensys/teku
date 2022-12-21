package tech.pegasys.teku.spec.propertytest.suppliers.type;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.propertytest.suppliers.DataStructureUtilSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlobBytesSupplier extends DataStructureUtilSupplier<Bytes> {

  public BlobBytesSupplier() {
    super(DataStructureUtil::randomBlobBytes, SpecMilestone.EIP4844);
  }
}
