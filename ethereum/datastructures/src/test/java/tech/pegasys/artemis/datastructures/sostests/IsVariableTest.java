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

package tech.pegasys.teku.datastructures.sostests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;

public class IsVariableTest {
  @Test
  void isBeaconBlockBodyVariableTest() {
    assertEquals(
        true, SimpleOffsetSerializer.classReflectionInfo.get(BeaconBlockBody.class).isVariable());
  }

  @Test
  void isBeaconBlockHeaderVariableTest() {
    assertEquals(
        false,
        SimpleOffsetSerializer.classReflectionInfo.get(BeaconBlockHeader.class).isVariable());
  }

  @Test
  void isBeaconBlockVariableTest() {
    assertEquals(
        true, SimpleOffsetSerializer.classReflectionInfo.get(BeaconBlock.class).isVariable());
  }

  @Test
  void isEth1DataVariableTest() {
    assertEquals(
        false, SimpleOffsetSerializer.classReflectionInfo.get(Eth1Data.class).isVariable());
  }

  @Test
  void isAttestationDataVariableTest() {
    assertEquals(
        false, SimpleOffsetSerializer.classReflectionInfo.get(AttestationData.class).isVariable());
  }

  @Test
  void isAttestationVariableTest() {
    assertEquals(
        true, SimpleOffsetSerializer.classReflectionInfo.get(Attestation.class).isVariable());
  }

  @Test
  void isAttesterSlashingVariableTest() {
    assertEquals(
        true, SimpleOffsetSerializer.classReflectionInfo.get(AttesterSlashing.class).isVariable());
  }

  @Test
  void isDepositDataVariableTest() {
    assertEquals(
        false, SimpleOffsetSerializer.classReflectionInfo.get(DepositData.class).isVariable());
  }

  @Test
  void isDepositVariableTest() {
    assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(Deposit.class).isVariable());
  }

  @Test
  void isIndexedAttestationVariableTest() {
    assertEquals(
        true,
        SimpleOffsetSerializer.classReflectionInfo.get(IndexedAttestation.class).isVariable());
  }

  @Test
  void isProposerSlashingVariableTest() {
    assertEquals(
        false, SimpleOffsetSerializer.classReflectionInfo.get(ProposerSlashing.class).isVariable());
  }

  @Test
  void isVoluntaryExitVariableTest() {
    assertEquals(
        false, SimpleOffsetSerializer.classReflectionInfo.get(VoluntaryExit.class).isVariable());
  }

  @Test
  void isBeaconStateVariableTest() {
    assertEquals(
        true, SimpleOffsetSerializer.classReflectionInfo.get(BeaconStateImpl.class).isVariable());
  }

  @Test
  void isCheckpointVariableTest() {
    assertEquals(
        false, SimpleOffsetSerializer.classReflectionInfo.get(Checkpoint.class).isVariable());
  }

  @Test
  void isForkVariableTest() {
    assertEquals(false, SimpleOffsetSerializer.classReflectionInfo.get(Fork.class).isVariable());
  }

  @Test
  void isHistoricalBatchVariableTest() {
    assertEquals(
        false, SimpleOffsetSerializer.classReflectionInfo.get(HistoricalBatch.class).isVariable());
  }

  @Test
  void isPendingAttestationVariableTest() {
    assertEquals(
        true,
        SimpleOffsetSerializer.classReflectionInfo.get(PendingAttestation.class).isVariable());
  }

  @Test
  void isValidatorVariableTest() {
    assertEquals(
        false, SimpleOffsetSerializer.classReflectionInfo.get(Validator.class).isVariable());
  }

  @Test
  void isAggregateAndProofVariableTest() {
    assertEquals(
        true, SimpleOffsetSerializer.classReflectionInfo.get(AggregateAndProof.class).isVariable());
  }

  @Test
  void isVoteTrackerVariableTest() {
    assertEquals(
        false, SimpleOffsetSerializer.classReflectionInfo.get(VoteTracker.class).isVariable());
  }
}
