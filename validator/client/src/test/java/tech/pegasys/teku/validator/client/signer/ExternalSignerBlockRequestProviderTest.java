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

package tech.pegasys.teku.validator.client.signer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExternalSignerBlockRequestProviderTest {

  @Test
  void phase0BlockGeneratesCorrectSignTypeAndMetadata() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final BeaconBlock block = new DataStructureUtil(spec).randomBeaconBlock(10);

    final ExternalSignerBlockRequestProvider externalSignerBlockRequestProvider =
        new ExternalSignerBlockRequestProvider(spec, block);
    final SignType signType = externalSignerBlockRequestProvider.getSignType();
    final Map<String, Object> blockMetadata =
        externalSignerBlockRequestProvider.getBlockMetadata(Map.of());

    assertThat(signType).isEqualTo(SignType.BLOCK);
    assertThat(blockMetadata).containsKey("block");
  }

  @Test
  void altairBlockGeneratesCorrectSignTypeAndMetadata() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final BeaconBlock block = new DataStructureUtil(spec).randomBeaconBlock(10);

    final ExternalSignerBlockRequestProvider externalSignerBlockRequestProvider =
        new ExternalSignerBlockRequestProvider(spec, block);
    final SignType signType = externalSignerBlockRequestProvider.getSignType();
    final Map<String, Object> blockMetadata =
        externalSignerBlockRequestProvider.getBlockMetadata(Map.of());

    assertThat(signType).isEqualTo(SignType.BLOCK_V2);
    assertThat(blockMetadata).containsKey("beacon_block");
    assertThat(blockMetadata.get("beacon_block")).isExactlyInstanceOf(BlockRequestBody.class);
  }
}
