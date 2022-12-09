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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyTest;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

class BeaconBlockBodyEip4844Test extends AbstractBeaconBlockBodyTest<BeaconBlockBodyEip4844> {

  protected SyncAggregate syncAggregate;
  protected ExecutionPayload executionPayload;
  protected SszList<SignedBlsToExecutionChange> blsToExecutionChanges;
  protected SszList<SszKZGCommitment> blobKzgCommitments;

  @BeforeEach
  void setup() {
    super.setUpBaseClass(
        SpecMilestone.EIP4844,
        () -> {
          syncAggregate = dataStructureUtil.randomSyncAggregate();
          executionPayload = dataStructureUtil.randomExecutionPayload();
          blsToExecutionChanges = dataStructureUtil.randomSignedBlsToExecutionChangesList();
          blobKzgCommitments = dataStructureUtil.randomSszKzgCommitmentList();
        });
  }

  @Test
  void equalsReturnsFalseWhenBlobKzgCommitmentsIsDifferent() {
    blobKzgCommitments = dataStructureUtil.randomSszKzgCommitmentList();
    BeaconBlockBodyAltair testBeaconBlockBody = safeJoin(createBlockBody());

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Override
  protected SafeFuture<BeaconBlockBodyEip4844> createBlockBody(
      final Consumer<BeaconBlockBodyBuilder> contentProvider) {
    return getBlockBodySchema()
        .createBlockBody(contentProvider)
        .thenApply(body -> (BeaconBlockBodyEip4844) body);
  }

  @Override
  protected Consumer<BeaconBlockBodyBuilder> createContentProvider() {
    return super.createContentProvider()
        .andThen(
            builder ->
                builder
                    .syncAggregate(() -> syncAggregate)
                    .executionPayload(() -> SafeFuture.completedFuture(executionPayload))
                    .blsToExecutionChanges(() -> blsToExecutionChanges)
                    .blobKzgCommitments(() -> SafeFuture.completedFuture(blobKzgCommitments)));
  }
}
