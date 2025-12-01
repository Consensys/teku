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

package tech.pegasys.teku.services.beaconchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerBasic;

class SingleBlockProviderResolverTest {

  private final DasSamplerBasic dasBasicSampler = Mockito.mock(DasSamplerBasic.class);
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool =
      Mockito.mock(BlockBlobSidecarsTrackersPool.class);
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldFetchBlockFromDasBasicSampler() {

    SignedBeaconBlock expectedBlock = dataStructureUtil.randomSignedBeaconBlock();
    when(dasBasicSampler.getBlock(expectedBlock.getRoot())).thenReturn(Optional.of(expectedBlock));

    SingleBlockProviderResolver resolver =
        new SingleBlockProviderResolver(
                blockBlobSidecarsTrackersPool::getBlock, dasBasicSampler::getBlock);
    SignedBeaconBlock actualBlock = resolver.getBlock(expectedBlock.getRoot()).get();

    assertEquals(expectedBlock, actualBlock);
  }

  @Test
  void shouldFetchBlockFromBlockBlobSidecarsTrackersPool() {

    SignedBeaconBlock expectedBlock = dataStructureUtil.randomSignedBeaconBlock();
    when(dasBasicSampler.getBlock(expectedBlock.getRoot())).thenReturn(Optional.empty());
    when(blockBlobSidecarsTrackersPool.getBlock(expectedBlock.getRoot()))
        .thenReturn(Optional.of(expectedBlock));

      SingleBlockProviderResolver resolver =
              new SingleBlockProviderResolver(
                      blockBlobSidecarsTrackersPool::getBlock, dasBasicSampler::getBlock);
    SignedBeaconBlock actualBlock = resolver.getBlock(expectedBlock.getRoot()).get();

    verify(dasBasicSampler).getBlock(expectedBlock.getRoot());
    verify(blockBlobSidecarsTrackersPool,times(2)).getBlock(expectedBlock.getRoot());
    assertEquals(expectedBlock, actualBlock);
  }

  @Test
  void shouldReturnEmptyIfBlockNotPresentInEitherSource() {

    SignedBeaconBlock expectedBlock = dataStructureUtil.randomSignedBeaconBlock();
    when(dasBasicSampler.getBlock(expectedBlock.getRoot())).thenReturn(Optional.empty());
    when(blockBlobSidecarsTrackersPool.getBlock(expectedBlock.getRoot()))
        .thenReturn(Optional.empty());

      SingleBlockProviderResolver resolver =
              new SingleBlockProviderResolver(
                      blockBlobSidecarsTrackersPool::getBlock, dasBasicSampler::getBlock);
    Optional<SignedBeaconBlock> actualBlock = resolver.getBlock(expectedBlock.getRoot());

    verify(dasBasicSampler).getBlock(expectedBlock.getRoot());
    verify(blockBlobSidecarsTrackersPool,times(2)).getBlock(expectedBlock.getRoot());
    assertEquals(Optional.empty(), actualBlock);
  }
}
