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

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyTest;

class BeaconBlockBodyAltairTest extends AbstractBeaconBlockBodyTest<BeaconBlockBodyAltair> {

  protected SyncAggregate syncAggregate;

  @BeforeEach
  void setup() {
    super.setUpBaseClass(
        SpecMilestone.ALTAIR, () -> syncAggregate = dataStructureUtil.randomSyncAggregate());
  }

  @Test
  void equalsReturnsFalseWhenSyncAggregateIsDifferent() {
    syncAggregate = dataStructureUtil.randomSyncAggregate();
    BeaconBlockBodyAltair testBeaconBlockBody = createBlockBody();

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Override
  protected BeaconBlockBodyAltair createBlockBody(
      final Consumer<BeaconBlockBodyBuilder> contentProvider) {
    return (BeaconBlockBodyAltair) getBlockBodySchema().createBlockBody(contentProvider);
  }

  @Override
  protected BeaconBlockBodySchema<? extends BeaconBlockBodyAltair> getBlockBodySchema() {
    return BeaconBlockBodySchemaAltair.create(spec.getGenesisSpecConfig());
  }

  @Override
  protected Consumer<BeaconBlockBodyBuilder> createContentProvider() {
    return super.createContentProvider()
        .andThen(builder -> builder.syncAggregate(() -> syncAggregate));
  }
}
