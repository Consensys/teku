/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.core;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.util.MockStartDepositGenerator;

public class TestDepositGenerator {
  DepositMerkleTree depositMerkleTree = new DepositMerkleTree();

  public TestDepositGenerator(List<BLSKeyPair> validatorKeys) {
    MockStartDepositGenerator depositGenerator = new MockStartDepositGenerator();
    List<DepositData> depositData = depositGenerator.createDeposits(validatorKeys);
    for (int i = 0; i < depositData.size(); i++) {
      depositMerkleTree.addDeposit(
          new DepositWithIndex(depositData.get(i), UnsignedLong.valueOf(i)));
    }
  }

  public List<Deposit> getDeposits(int fromIndex, int toIndex, int eth1DepositCount) {
    return depositMerkleTree.getDepositsWithProof(
        UnsignedLong.valueOf(fromIndex),
        UnsignedLong.valueOf(toIndex),
        UnsignedLong.valueOf(eth1DepositCount));
  }

  public Bytes32 getRoot() {
    return depositMerkleTree.getRoot();
  }
}
