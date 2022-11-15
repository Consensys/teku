/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container12;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadEip4844;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadEip4844Impl;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class BeaconBlockBodyEip4844Impl
    extends Container12<
        BeaconBlockBodyEip4844Impl,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>,
        SyncAggregate,
        ExecutionPayloadEip4844Impl,
        SszList<SignedBlsToExecutionChange>,
        SszList<SszKZGCommitment>>
    implements BeaconBlockBodyEip4844 {

  BeaconBlockBodyEip4844Impl(
      BeaconBlockBodySchemaEip4844Impl type,
      SszSignature randaoReveal,
      Eth1Data eth1Data,
      SszBytes32 graffiti,
      SszList<ProposerSlashing> proposerSlashings,
      SszList<AttesterSlashing> attesterSlashings,
      SszList<Attestation> attestations,
      SszList<Deposit> deposits,
      SszList<SignedVoluntaryExit> voluntaryExits,
      SyncAggregate syncAggregate,
      ExecutionPayloadEip4844Impl executionPayload,
      SszList<SignedBlsToExecutionChange> blsToExecutionChanges,
      SszList<SszKZGCommitment> blobKzgCommitments) {
    super(
        type,
        randaoReveal,
        eth1Data,
        graffiti,
        proposerSlashings,
        attesterSlashings,
        attestations,
        deposits,
        voluntaryExits,
        syncAggregate,
        executionPayload,
        blsToExecutionChanges,
        blobKzgCommitments);
  }

  BeaconBlockBodyEip4844Impl(final BeaconBlockBodySchemaEip4844Impl type) {
    super(type);
  }

  BeaconBlockBodyEip4844Impl(
      final BeaconBlockBodySchemaEip4844Impl type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public BLSSignature getRandaoReveal() {
    return getField0().getSignature();
  }

  @Override
  public SszSignature getRandaoRevealSsz() {
    return getField0();
  }

  @Override
  public Eth1Data getEth1Data() {
    return getField1();
  }

  @Override
  public Bytes32 getGraffiti() {
    return getField2().get();
  }

  @Override
  public SszBytes32 getGraffitiSsz() {
    return getField2();
  }

  @Override
  public SszList<ProposerSlashing> getProposerSlashings() {
    return getField3();
  }

  @Override
  public SszList<AttesterSlashing> getAttesterSlashings() {
    return getField4();
  }

  @Override
  public SszList<Attestation> getAttestations() {
    return getField5();
  }

  @Override
  public SszList<Deposit> getDeposits() {
    return getField6();
  }

  @Override
  public SszList<SignedVoluntaryExit> getVoluntaryExits() {
    return getField7();
  }

  @Override
  public SyncAggregate getSyncAggregate() {
    return getField8();
  }

  @Override
  public ExecutionPayloadEip4844 getExecutionPayload() {
    return getField9();
  }

  @Override
  public SszList<SignedBlsToExecutionChange> getBlsToExecutionChanges() {
    return getField10();
  }

  @Override
  public SszList<SszKZGCommitment> getBlobKzgCommitments() {
    return getField11();
  }

  @Override
  public Optional<BeaconBlockBodyEip4844> toVersionEip4844() {
    return Optional.of(this);
  }

  @Override
  public BeaconBlockBodySchemaEip4844Impl getSchema() {
    return (BeaconBlockBodySchemaEip4844Impl) super.getSchema();
  }
}
