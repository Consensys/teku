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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyTest;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class BeaconBlockBodyPhase0Test extends AbstractBeaconBlockBodyTest<BeaconBlockBodyPhase0> {

  @Override
  protected BeaconBlockBodyPhase0 createBlockBody(
      final BLSSignature randaoReveal,
      final Eth1Data eth1Data,
      final Bytes32 graffiti,
      final SSZList<ProposerSlashing> proposerSlashings,
      final SSZList<AttesterSlashing> attesterSlashings,
      final SSZList<Attestation> attestations,
      final SSZList<Deposit> deposits,
      final SSZList<SignedVoluntaryExit> voluntaryExits) {
    return getBlockBodySchema()
        .createBlockBody(
            randaoReveal,
            eth1Data,
            graffiti,
            proposerSlashings,
            attesterSlashings,
            attestations,
            deposits,
            voluntaryExits);
  }

  @Override
  protected BeaconBlockBodySchema<BeaconBlockBodyPhase0> getBlockBodySchema() {
    return BeaconBlockBodySchemaPhase0.create(spec.getGenesisSpecConstants());
  }
}
