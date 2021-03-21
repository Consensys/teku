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

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyTest;

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
      final Consumer<BeaconBlockBodyBuilder> contentProvider) {
    return getBlockBodySchema().createBlockBody(contentProvider);
  }

  @Override
  protected BeaconBlockBodySchema<BeaconBlockBodyAltair> getBlockBodySchema() {
    return BeaconBlockBodySchemaAltair.create(spec.getGenesisSpecConfig());
  }
}
