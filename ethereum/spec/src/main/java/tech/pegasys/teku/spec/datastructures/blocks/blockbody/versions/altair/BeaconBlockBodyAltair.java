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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair;

import java.util.Optional;
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
import tech.pegasys.teku.ssz.containers.Container9;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.tree.TreeNode;

/** A Beacon block body */
public class BeaconBlockBodyAltair
    extends Container9<
        BeaconBlockBodyAltair,
        SszSignature,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>,
        SyncAggregate>
    implements BeaconBlockBody {

  BeaconBlockBodyAltair(BeaconBlockBodySchemaAltair type) {
    super(type);
  }

  BeaconBlockBodyAltair(BeaconBlockBodySchemaAltair type, TreeNode backingNode) {
    super(type, backingNode);
  }

  BeaconBlockBodyAltair(
      BeaconBlockBodySchemaAltair type,
      SszSignature randaoReveal,
      Eth1Data eth1Data,
      SszBytes32 graffiti,
      SszList<ProposerSlashing> proposerSlashings,
      SszList<AttesterSlashing> attesterSlashings,
      SszList<Attestation> attestations,
      SszList<Deposit> deposits,
      SszList<SignedVoluntaryExit> voluntaryExits,
      SyncAggregate syncAggregate) {
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
        syncAggregate);
  }

  public static BeaconBlockBodyAltair required(final BeaconBlockBody body) {
    return body.toVersionAltair()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected altair block body but got " + body.getClass().getSimpleName()));
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

  public SyncAggregate getSyncAggregate() {
    return getField8();
  }

  @Override
  public BeaconBlockBodySchemaAltair getSchema() {
    return (BeaconBlockBodySchemaAltair) super.getSchema();
  }

  @Override
  public Optional<BeaconBlockBodyAltair> toVersionAltair() {
    return Optional.of(this);
  }
}
