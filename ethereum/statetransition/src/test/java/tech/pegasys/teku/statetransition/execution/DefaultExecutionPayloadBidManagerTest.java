/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DefaultExecutionPayloadBidManagerTest {

  private final Spec spec = TestSpecFactory.createMainnetGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final BlockProductionPerformance blockProductionPerformance =
      mock(BlockProductionPerformance.class);

  private final DefaultExecutionPayloadBidManager executionPayloadBidManager =
      new DefaultExecutionPayloadBidManager(spec);

  @Test
  public void createsLocalBidForBlock() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    final ExecutionPayload executionPayload =
        dataStructureUtil.randomExecutionPayload(state.getSlot());
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle(3);

    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(state.getSlot()).getSchemaDefinitions());

    final GetPayloadResponse getPayloadResponse =
        new GetPayloadResponse(
            executionPayload,
            UInt256.valueOf(1000000000000L),
            blobsBundle,
            false,
            dataStructureUtil.randomExecutionRequests());

    final Optional<SignedExecutionPayloadBid> maybeSignedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                state, SafeFuture.completedFuture(getPayloadResponse), blockProductionPerformance));

    assertThat(maybeSignedBid).isPresent();

    final SignedExecutionPayloadBid signedBid = maybeSignedBid.get();

    assertThat(signedBid.getSignature()).isEqualTo(BLSSignature.infinity());

    final ExecutionPayloadBid bid = signedBid.getMessage();

    final Bytes32 expectedBlobKzgCommitmentsRoot =
        schemaDefinitions
            .getBlobKzgCommitmentsSchema()
            .createFromBlobsBundle(blobsBundle)
            .hashTreeRoot();
    final ExecutionPayloadBid expectedBid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .createLocalSelfBuiltBid(
                BUILDER_INDEX_SELF_BUILD,
                state.getSlot(),
                state,
                executionPayload,
                expectedBlobKzgCommitmentsRoot);

    assertThat(bid).isEqualTo(expectedBid);
  }
}
