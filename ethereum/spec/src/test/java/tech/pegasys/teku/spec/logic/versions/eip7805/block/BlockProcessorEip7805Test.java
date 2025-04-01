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

package tech.pegasys.teku.spec.logic.versions.eip7805.block;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.electra.block.BlockProcessorElectraTest;

public class BlockProcessorEip7805Test extends BlockProcessorElectraTest {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetEip7805();
  }

  @Test
  @Override
  public void shouldCreateNewPayloadRequest() throws BlockProcessingException {
    final BeaconState preState = createBeaconState();
    final BeaconBlockBody blockBody = dataStructureUtil.randomBeaconBlockBodyWithCommitments(3);
    final MiscHelpers miscHelpers = spec.atSlot(UInt64.ONE).miscHelpers();
    final List<VersionedHash> expectedVersionedHashes =
        blockBody.getOptionalBlobKzgCommitments().orElseThrow().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(miscHelpers::kzgCommitmentToVersionedHash)
            .collect(Collectors.toList());
    final InclusionList inclusionList = dataStructureUtil.randomInclusionList();

    final NewPayloadRequest newPayloadRequest =
        spec.getBlockProcessor(UInt64.ONE)
            .computeNewPayloadRequest(preState, blockBody, Optional.of(List.of(inclusionList)));

    assertThat(newPayloadRequest.getExecutionPayload())
        .isEqualTo(blockBody.getOptionalExecutionPayload().orElseThrow());
    assertThat(newPayloadRequest.getVersionedHashes()).isPresent();
    assertThat(newPayloadRequest.getVersionedHashes().get())
        .hasSize(3)
        .allSatisfy(
            versionedHash ->
                assertThat(versionedHash.getVersion())
                    .isEqualTo(SpecConfigDeneb.VERSIONED_HASH_VERSION_KZG))
        .hasSameElementsAs(expectedVersionedHashes);
    assertThat(newPayloadRequest.getParentBeaconBlockRoot()).isPresent();
    assertThat(newPayloadRequest.getParentBeaconBlockRoot().get())
        .isEqualTo(preState.getLatestBlockHeader().getParentRoot());
    assertThat(newPayloadRequest.getInclusionList()).isPresent();
    assertThat(newPayloadRequest.getInclusionList().get())
        .isEqualTo(inclusionList.getTransactions());
  }
}
