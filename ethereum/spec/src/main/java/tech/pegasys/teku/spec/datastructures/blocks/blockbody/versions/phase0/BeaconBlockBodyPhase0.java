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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.containers.Container8;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.tree.TreeNode;

/** A Beacon block body */
class BeaconBlockBodyPhase0
    extends Container8<
        BeaconBlockBodyPhase0,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>>
    implements BeaconBlockBody {

  BeaconBlockBodyPhase0(BeaconBlockBodySchemaPhase0 type) {
    super(type);
  }

  BeaconBlockBodyPhase0(BeaconBlockBodySchemaPhase0 type, TreeNode backingNode) {
    super(type, backingNode);
  }

  BeaconBlockBodyPhase0(
      BeaconBlockBodySchemaPhase0 type,
      SszSignature randao_reveal,
      Eth1Data eth1_data,
      SszBytes32 graffiti,
      SszList<ProposerSlashing> proposer_slashings,
      SszList<AttesterSlashing> attester_slashings,
      SszList<Attestation> attestations,
      SszList<Deposit> deposits,
      SszList<SignedVoluntaryExit> voluntary_exits) {
    super(
        type,
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits);
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

  @Override
  public BeaconBlockBodySchemaPhase0 getSchema() {
    return (BeaconBlockBodySchemaPhase0) super.getSchema();
  }
}
