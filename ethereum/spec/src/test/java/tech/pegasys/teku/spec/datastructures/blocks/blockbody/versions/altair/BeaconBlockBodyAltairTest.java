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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyTest;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.ssz.SszList;

class BeaconBlockBodyAltairTest extends AbstractBeaconBlockBodyTest<BeaconBlockBodyAltair> {

  @Test
  void shouldCreateWithEmtpySyncAggregate() {
    // This won't always be true but until we can calculate the actual SyncAggregate, use the empty
    // one to make the block valid

    final BeaconBlockBodyAltair blockBody = createDefaultBlockBody();
    final SyncAggregate emptySyncAggregate =
        SyncAggregateSchema.create(
                spec.getGenesisSpecConfig().toVersionAltair().orElseThrow().getSyncCommitteeSize())
            .createEmpty();
    assertThat(blockBody.getSyncAggregate()).isEqualTo(emptySyncAggregate);
  }

  @Override
  protected BeaconBlockBodyAltair createBlockBody(
      final BLSSignature randaoReveal,
      final Eth1Data eth1Data,
      final Bytes32 graffiti,
      final SszList<ProposerSlashing> proposerSlashings,
      final SszList<AttesterSlashing> attesterSlashings,
      final SszList<Attestation> attestations,
      final SszList<Deposit> deposits,
      final SszList<SignedVoluntaryExit> voluntaryExits) {
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
  protected BeaconBlockBodySchema<BeaconBlockBodyAltair> getBlockBodySchema() {
    return BeaconBlockBodySchemaAltair.create(spec.getGenesisSpecConfig());
  }
}
