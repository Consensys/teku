package tech.pegasys.artemis.datastructures.operations;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

public class DepositWithIndex extends Deposit{

  private UnsignedLong index;

  public DepositWithIndex(SSZVector<Bytes32> proof, DepositData data, UnsignedLong index) {
    super(proof, data);
    this.index = index;
  }

  public DepositWithIndex() {
    super();
    this.index = null;
  }

  public DepositWithIndex(DepositData data, UnsignedLong index) {
    super(data);
    this.index = index;
  }

  public UnsignedLong getIndex() {
    return index;
  }
}
