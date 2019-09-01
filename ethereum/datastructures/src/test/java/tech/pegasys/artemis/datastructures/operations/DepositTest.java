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

package tech.pegasys.artemis.datastructures.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDepositData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

@ExtendWith(BouncyCastleExtension.class)
class DepositTest {

  private SSZVector<Bytes32> branch =
      new SSZVector<>(Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1, Bytes32.ZERO);
  private DepositData depositData = randomDepositData();

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
    List<Bytes32> reverseBranch = new ArrayList<>(branch);
    Collections.reverse(reverseBranch);

    Deposit testDeposit = new Deposit(new SSZVector<>(reverseBranch, Bytes32.class), depositData);

    assertNotEquals(deposit, testDeposit);
  }

  @Test
  void equalsReturnsFalseWhenDepositDataIsDifferent() {
    // DepositData is rather involved to create. Just create a random one until it is not the same
    // as the original.
    DepositData otherDepositData = randomDepositData();
    while (Objects.equals(otherDepositData, depositData)) {
      otherDepositData = randomDepositData();
    }

    Deposit testDeposit = new Deposit(branch, otherDepositData);

    assertNotEquals(deposit, testDeposit);
  }

  @Test
  void roundtripSSZ() {
    // todo
    // deposit.setProof(Collections.nCopies(32, Bytes32.random()));

    // Bytes sszDepositBytes = deposit.toBytes();
    // Deposit sszDeposit = Deposit.fromBytes(sszDepositBytes);
    // assertEquals(deposit, sszDeposit);
  }

  @Test
  void vectorLengthsTest() {
    List<Integer> vectorLengths = List.of(Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1);
    assertEquals(
        vectorLengths,
        SimpleOffsetSerializer.classReflectionInfo.get(Deposit.class).getVectorLengths());
  }
}
