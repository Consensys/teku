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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import org.junit.jupiter.api.Test;

class DepositInputTest {

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    Bytes48 pubkey = Bytes48.random();
    Bytes32 withdrawalCredentials = Bytes32.random();
    List<Bytes48> proofOfPossession = Arrays.asList(Bytes48.random(), Bytes48.random());

    DepositInput di1 = new DepositInput(pubkey, withdrawalCredentials, proofOfPossession);
    DepositInput di2 = di1;

    assertEquals(di1, di2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Bytes48 pubkey = Bytes48.random();
    Bytes32 withdrawalCredentials = Bytes32.random();
    List<Bytes48> proofOfPossession = Arrays.asList(Bytes48.random(), Bytes48.random());

    DepositInput di1 = new DepositInput(pubkey, withdrawalCredentials, proofOfPossession);
    DepositInput di2 = new DepositInput(pubkey, withdrawalCredentials, proofOfPossession);

    assertEquals(di1, di2);
  }

  @Test
  void equalsReturnsFalseWhenPubkeysAreDifferent() {
    Bytes48 pubkey = Bytes48.random();
    Bytes32 withdrawalCredentials = Bytes32.random();
    List<Bytes48> proofOfPossession = Arrays.asList(Bytes48.random(), Bytes48.random());

    DepositInput di1 = new DepositInput(pubkey, withdrawalCredentials, proofOfPossession);
    DepositInput di2 = new DepositInput(pubkey.not(), withdrawalCredentials, proofOfPossession);

    assertNotEquals(di1, di2);
  }

  @Test
  void equalsReturnsFalseWhenWithdrawalCredentialsAreDifferent() {
    Bytes48 pubkey = Bytes48.random();
    Bytes32 withdrawalCredentials = Bytes32.random();
    List<Bytes48> proofOfPossession = Arrays.asList(Bytes48.random(), Bytes48.random());

    DepositInput di1 = new DepositInput(pubkey, withdrawalCredentials, proofOfPossession);
    DepositInput di2 = new DepositInput(pubkey, withdrawalCredentials.not(), proofOfPossession);

    assertNotEquals(di1, di2);
  }

  @Test
  void equalsReturnsFalseWhenProofsOfPosessionAreDifferent() {
    Bytes48 pubkey = Bytes48.random();
    Bytes32 withdrawalCredentials = Bytes32.random();
    List<Bytes48> proofOfPossession = Arrays.asList(Bytes48.random(), Bytes48.random());

    // Create copy of signature and reverse to ensure it is different.
    List<Bytes48> reverseProofOfPossession = new ArrayList<Bytes48>(proofOfPossession);
    Collections.reverse(reverseProofOfPossession);

    DepositInput di1 = new DepositInput(pubkey, withdrawalCredentials, proofOfPossession);
    DepositInput di2 = new DepositInput(pubkey, withdrawalCredentials, reverseProofOfPossession);

    assertNotEquals(di1, di2);
  }

  @Test
  void rountripSSZ() {
    DepositInput depositInput =
        new DepositInput(
            Bytes48.random(),
            Bytes32.random(),
            Arrays.asList(Bytes48.random(), Bytes48.random(), Bytes48.random()));
    Bytes sszDepositInputBytes = depositInput.toBytes();
    assertEquals(depositInput, DepositInput.fromBytes(sszDepositInputBytes));
  }
}
