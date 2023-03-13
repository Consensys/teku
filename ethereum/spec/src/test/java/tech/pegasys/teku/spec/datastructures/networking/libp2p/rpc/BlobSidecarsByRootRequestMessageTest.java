/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;

class BlobSidecarsByRootRequestMessageTest {

  @Test
  public void shouldRoundTripViaSsz() {
    final BlobSidecarsByRootRequestMessage request =
        new BlobSidecarsByRootRequestMessage(
            List.of(
                new BlobIdentifier(
                    Bytes32.fromHexString(
                        "0x1111111111111111111111111111111111111111111111111111111111111111"),
                    UInt64.ZERO),
                new BlobIdentifier(
                    Bytes32.fromHexString(
                        "0x2222222222222222222222222222222222222222222222222222222222222222"),
                    UInt64.ONE)));
    final Bytes data = request.sszSerialize();
    final BlobSidecarsByRootRequestMessage result =
        BlobSidecarsByRootRequestMessage.SSZ_SCHEMA.sszDeserialize(data);
    assertThatSszData(result).isEqualByAllMeansTo(request);
  }

  @Test
  public void verifyMaxLengthOfContainerIsGreaterOrEqualToMaxRequestBlobSidecars() {
    final List<SpecMilestone> shardingMilestones =
        SpecMilestone.getAllFutureMilestones(SpecMilestone.CAPELLA);

    shardingMilestones.forEach(
        milestone -> {
          final Spec spec = TestSpecFactory.createMainnet(milestone);
          final UInt64 maxRequestBlobSidecars =
              SpecConfigDeneb.required(spec.forMilestone(milestone).getConfig())
                  .getMaxRequestBlobSidecars();
          assertThat(BeaconBlocksByRootRequestMessage.SSZ_SCHEMA.getMaxLength())
              .isGreaterThanOrEqualTo(maxRequestBlobSidecars.longValue());
        });
  }
}
