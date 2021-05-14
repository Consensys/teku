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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.rayonism;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.containers.Container9;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.tree.TreeNode;

/** A Beacon block body */
public class BeaconBlockBodyRayonism
    extends Container9<
        BeaconBlockBodyRayonism,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>,
        ExecutionPayload>
    implements BeaconBlockBody {

  BeaconBlockBodyRayonism(BeaconBlockBodySchemaRayonism type) {
    super(type);
  }

  BeaconBlockBodyRayonism(BeaconBlockBodySchemaRayonism type, TreeNode backingNode) {
    super(type, backingNode);
  }

  BeaconBlockBodyRayonism(
      BeaconBlockBodySchemaRayonism type,
      SszSignature randao_reveal,
      Eth1Data eth1_data,
      SszBytes32 graffiti,
      SszList<ProposerSlashing> proposer_slashings,
      SszList<AttesterSlashing> attester_slashings,
      SszList<Attestation> attestations,
      SszList<Deposit> deposits,
      SszList<SignedVoluntaryExit> voluntary_exits,
      ExecutionPayload execution_payload) {
    super(
        type,
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits,
        execution_payload);
  }

  public static BeaconBlockBodyRayonism required(final BeaconBlockBody body) {
    checkArgument(
        body instanceof BeaconBlockBodyRayonism,
        "Expected merge block body but got %s",
        body.getClass());
    return (BeaconBlockBodyRayonism) body;
  }

  @Override
  public BLSSignature getRandao_reveal() {
    return getField0().getSignature();
  }

  @Override
  public Eth1Data getEth1_data() {
    return getField1();
  }

  @Override
  public Bytes32 getGraffiti() {
    return getField2().get();
  }

  @Override
  public SszList<ProposerSlashing> getProposer_slashings() {
    return getField3();
  }

  @Override
  public SszList<AttesterSlashing> getAttester_slashings() {
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
  public SszList<SignedVoluntaryExit> getVoluntary_exits() {
    return getField7();
  }

  public ExecutionPayload getExecution_payload() {
    return getField8();
  }

  @Override
  public BeaconBlockBodySchemaRayonism getSchema() {
    return (BeaconBlockBodySchemaRayonism) super.getSchema();
  }

  @Override
  public Optional<BeaconBlockBodyRayonism> toVersionRayonism() {
    return Optional.of(this);
  }
}
