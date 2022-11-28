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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container10;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadHeaderBellatrix;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

/** A blinded Beacon block body */
class BlindedBeaconBlockBodyBellatrixImpl
    extends Container10<
        BlindedBeaconBlockBodyBellatrixImpl,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>,
        SyncAggregate,
        ExecutionPayloadHeaderBellatrix>
    implements BlindedBeaconBlockBodyBellatrix {

  BlindedBeaconBlockBodyBellatrixImpl(BlindedBeaconBlockBodySchemaBellatrixImpl type) {
    super(type);
  }

  BlindedBeaconBlockBodyBellatrixImpl(
      BlindedBeaconBlockBodySchemaBellatrixImpl type, TreeNode backingNode) {
    super(type, backingNode);
  }

  BlindedBeaconBlockBodyBellatrixImpl(
      BlindedBeaconBlockBodySchemaBellatrixImpl type,
      SszSignature randaoReveal,
      Eth1Data eth1Data,
      SszBytes32 graffiti,
      SszList<ProposerSlashing> proposerSlashings,
      SszList<AttesterSlashing> attesterSlashings,
      SszList<Attestation> attestations,
      SszList<Deposit> deposits,
      SszList<SignedVoluntaryExit> voluntaryExits,
      SyncAggregate syncAggregate,
      ExecutionPayloadHeaderBellatrix executionPayloadHeader) {
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
        executionPayloadHeader);
  }

  public static BlindedBeaconBlockBodyBellatrixImpl required(final BeaconBlockBody body) {
    checkArgument(
        body instanceof BlindedBeaconBlockBodyBellatrixImpl,
        "Expected bellatrix blinded block body but got %s",
        body.getClass());
    return (BlindedBeaconBlockBodyBellatrixImpl) body;
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
  public Optional<SszList<SignedBlsToExecutionChange>> getOptionalBlsToExecutionChanges() {
    return Optional.empty();
  }

  @Override
  public SyncAggregate getSyncAggregate() {
    return getField8();
  }

  @Override
  public ExecutionPayloadHeader getExecutionPayloadHeader() {
    return getField9();
  }

  @Override
  public BlindedBeaconBlockBodySchemaBellatrixImpl getSchema() {
    return (BlindedBeaconBlockBodySchemaBellatrixImpl) super.getSchema();
  }
}
