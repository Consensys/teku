/*
 * Copyright 2021 ConsenSys AG.
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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container10;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

/** A Beacon block body */
class BeaconBlockBodyBellatrixStorage
    extends Container10<
        BeaconBlockBodyBellatrixStorage,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>,
        SyncAggregate,
        SszBytes32> {

  public static final int EXECUTION_PAYLOAD_INDEX = 9;

  BeaconBlockBodyBellatrixStorage(BeaconBlockBodySchemaBellatrixStorage type) {
    super(type);
  }

  BeaconBlockBodyBellatrixStorage(
      BeaconBlockBodySchemaBellatrixStorage type, TreeNode backingNode) {
    super(type, backingNode);
  }

  BeaconBlockBodyBellatrixStorage(
      BeaconBlockBodySchemaBellatrixStorage type,
      SszSignature randaoReveal,
      Eth1Data eth1Data,
      SszBytes32 graffiti,
      SszList<ProposerSlashing> proposerSlashings,
      SszList<AttesterSlashing> attesterSlashings,
      SszList<Attestation> attestations,
      SszList<Deposit> deposits,
      SszList<SignedVoluntaryExit> voluntaryExits,
      SyncAggregate syncAggregate,
      SszBytes32 executionPayloadRoot) {
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
        executionPayloadRoot);
  }

  @Override
  public BeaconBlockBodySchemaBellatrixStorage getSchema() {
    return (BeaconBlockBodySchemaBellatrixStorage) super.getSchema();
  }

  public Bytes32 getExecutionPayloadRoot() {
    return getField9().get();
  }

  public BeaconBlockBodyBellatrix withExecutionPayload(
      final BeaconBlockBodySchemaBellatrix<?> newSchema, final ExecutionPayload payload) {
    final BeaconBlockBodySchemaBellatrixStorage schema = getSchema();
    final long payloadIndex = schema.getChildGeneralizedIndex(EXECUTION_PAYLOAD_INDEX);
    final TreeNode fullTree = getBackingNode().updated(payloadIndex, payload.getBackingNode());
    return newSchema.createFromBackingNode(fullTree);
  }
}
