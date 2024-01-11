/*
 * Copyright Consensys Software Inc., 2022
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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyTest;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;

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
    BeaconBlockBodyAltair testBeaconBlockBody = safeJoin(createBlockBody());

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Override
  protected SafeFuture<BeaconBlockBodyAltair> createBlockBody(
      final Consumer<BeaconBlockBodyBuilder> contentProvider) {
    final BeaconBlockBodyBuilder bodyBuilder = createBeaconBlockBodyBuilder();
    contentProvider.accept(bodyBuilder);
    return bodyBuilder.build().thenApply(body -> body.toVersionAltair().orElseThrow());
  }

  @Override
  protected SafeFuture<BlindedBeaconBlockBodyBellatrix> createBlindedBlockBody(
      Consumer<BeaconBlockBodyBuilder> contentProvider) {
    return SafeFuture.completedFuture(null);
  }

  @Override
  protected SafeFuture<BlindedBeaconBlockBodyBellatrix> createDefaultBlindedBlockBody() {
    return SafeFuture.completedFuture(null);
  }

  @Override
  protected Consumer<BeaconBlockBodyBuilder> createContentProvider(final boolean blinded) {
    return super.createContentProvider(blinded)
        .andThen(builder -> builder.syncAggregate(syncAggregate));
  }
}
