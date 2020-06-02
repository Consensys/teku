package tech.pegasys.teku.core;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.util.MockStartDepositGenerator;
import java.util.List;

public class TestDepositGenerator {
  DepositMerkleTree depositMerkleTree = new DepositMerkleTree();

  public TestDepositGenerator(List<BLSKeyPair> validatorKeys) {
    MockStartDepositGenerator depositGenerator = new MockStartDepositGenerator();
    List<DepositData> depositData = depositGenerator.createDeposits(validatorKeys);
    for (int i = 0; i < depositData.size(); i++) {
      depositMerkleTree.addDeposit(new DepositWithIndex(depositData.get(i), UnsignedLong.valueOf(i)));
    }
  }

  public List<Deposit> getDeposits(int fromIndex, int toIndex, int eth1DepositCount) {
    return depositMerkleTree.getDepositsWithProof(
            UnsignedLong.valueOf(fromIndex),
            UnsignedLong.valueOf(toIndex),
            UnsignedLong.valueOf(eth1DepositCount)
    );
  }

  public Bytes32 getRoot() {
    return depositMerkleTree.getRoot();
  }
}
