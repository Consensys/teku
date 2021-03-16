/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SszTestUtils;
import tech.pegasys.teku.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.util.config.Constants;

@ExtendWith(BouncyCastleExtension.class)
class DepositTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private SszBytes32Vector branch = setupMerkleBranch();
  private DepositData depositData = dataStructureUtil.randomDepositData();

  private Deposit deposit = new Deposit(branch, depositData);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    Deposit testDeposit = deposit;

    assertEquals(deposit, testDeposit);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Deposit testDeposit = new Deposit(branch, depositData);

    assertEquals(deposit, testDeposit);
  }

  @Test
  void equalsReturnsFalseWhenBranchesAreDifferent() {
    // Create copy of signature and reverse to ensure it is different.

    List<Bytes32> reverseBranch = new ArrayList<>(branch.asListUnboxed());
    Collections.reverse(reverseBranch);

    Deposit testDeposit =
        new Deposit(Deposit.SSZ_SCHEMA.getProofSchema().of(reverseBranch), depositData);

    assertNotEquals(deposit, testDeposit);
  }

  @Test
  void equalsReturnsFalseWhenDepositDataIsDifferent() {
    // DepositData is rather involved to create. Just create a random one until it is not the same
    // as the original.
    DepositData otherDepositData = dataStructureUtil.randomDepositData();
    while (Objects.equals(otherDepositData, depositData)) {
      otherDepositData = dataStructureUtil.randomDepositData();
    }

    Deposit testDeposit = new Deposit(branch, otherDepositData);

    assertNotEquals(deposit, testDeposit);
  }

  @Test
  void roundtripSSZ() {
    Deposit deposit = dataStructureUtil.randomDeposit();
    Bytes serialized = deposit.sszSerialize();
    Deposit newDeposit = Deposit.SSZ_SCHEMA.sszDeserialize(serialized);
    assertEquals(deposit, newDeposit);
  }

  @Test
  void vectorLengthsTest() {
    List<Integer> vectorLengths = List.of(Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1);
    assertEquals(vectorLengths, SszTestUtils.getVectorLengths(Deposit.SSZ_SCHEMA));
  }

  private SszBytes32Vector setupMerkleBranch() {
    return new DataStructureUtil()
        .randomSszBytes32Vector(
            Deposit.SSZ_SCHEMA.getProofSchema(), (Supplier<Bytes32>) Bytes32::random);
  }
}
