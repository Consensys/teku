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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlindedBlockContainer;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {SpecMilestone.BELLATRIX, SpecMilestone.CAPELLA, SpecMilestone.DENEB})
class BlindBlockUtilTest {

  private SpecMilestone specMilestone;
  private DataStructureUtil dataStructureUtil;
  private BlindBlockUtil blindBlockUtil;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    final Spec spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    dataStructureUtil = specContext.getDataStructureUtil();
    final SpecVersion specVersion = spec.forMilestone(specContext.getSpecMilestone());
    blindBlockUtil = specVersion.getBlindBlockUtil().orElseThrow();
  }

  @TestTemplate
  void shouldBlindAndUnblindBlock() {
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock();
    assertThat(signedBeaconBlock.isBlinded()).isFalse();
    final SignedBeaconBlock signedBlindedBeaconBlock =
        blindBlockUtil.blindSignedBeaconBlock(signedBeaconBlock);
    assertThat(signedBlindedBeaconBlock.isBlinded()).isTrue();
    assertThat(signedBlindedBeaconBlock.getBodyRoot()).isEqualTo(signedBeaconBlock.getBodyRoot());
    assertThat(signedBlindedBeaconBlock.getMessage().getBody().getOptionalExecutionPayload())
        .isEmpty();
    assertThat(signedBlindedBeaconBlock.getMessage().getBody().getOptionalExecutionPayloadHeader())
        .isNotEmpty();

    final SignedBlindedBlockContainer signedBlindedBlockContainer;
    if (specMilestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      signedBlindedBlockContainer =
          dataStructureUtil.randomSignedBlindedBlockContents(signedBlindedBeaconBlock);
    } else {
      signedBlindedBlockContainer = signedBlindedBeaconBlock;
    }

    final SafeFuture<SignedBeaconBlock> signedBeaconBlockSafeFuture =
        blindBlockUtil.unblindSignedBeaconBlock(
            signedBlindedBlockContainer,
            unblinder ->
                unblinder.setExecutionPayloadSupplier(
                    () ->
                        SafeFuture.completedFuture(
                            signedBeaconBlock
                                .getBeaconBlock()
                                .orElseThrow()
                                .getBody()
                                .getOptionalExecutionPayload()
                                .orElseThrow())));

    final SignedBeaconBlock unblindedSignedBeaconBlock =
        signedBeaconBlockSafeFuture.getImmediately();
    assertThat(unblindedSignedBeaconBlock).isEqualTo(signedBeaconBlock);
    assertThat(
            unblindedSignedBeaconBlock
                .getBeaconBlock()
                .orElseThrow()
                .getBody()
                .getOptionalExecutionPayload())
        .isNotEmpty();
    assertThat(
            unblindedSignedBeaconBlock
                .getBeaconBlock()
                .orElseThrow()
                .getBody()
                .getOptionalExecutionPayloadHeader())
        .isEmpty();
  }
}
