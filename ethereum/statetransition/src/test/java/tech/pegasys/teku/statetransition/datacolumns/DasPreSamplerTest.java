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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler.SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH;
import static tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public class DasPreSamplerTest {

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final DataAvailabilitySampler sampler = mock(DataAvailabilitySampler.class);
  private final DataColumnSidecarCustody custody = mock(DataColumnSidecarCustody.class);
  private final CustodyGroupCountManager custodyGroupCountManager =
      mock(CustodyGroupCountManager.class);

  private final DasPreSampler dasPreSampler =
      new DasPreSampler(sampler, custody, custodyGroupCountManager);

  @Test
  void onNewPreImportBlocks_shouldNotSampleWhenEligibilityIsNotRequired() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    when(sampler.checkSamplingEligibility(block.getMessage())).thenReturn(NOT_REQUIRED_OLD_EPOCH);

    dasPreSampler.onNewPreImportBlocks(List.of(block));

    verify(sampler).checkSamplingEligibility(block.getMessage());
    verify(sampler).flush();
    verifyNoMoreInteractions(sampler);
    verifyNoInteractions(custody, custodyGroupCountManager);
  }

  @Test
  void shouldFilterAndProcessOnlyRequiredBlocks() {
    final SignedBeaconBlock blockToSample = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock blockToSkip = dataStructureUtil.randomSignedBeaconBlock(2);

    when(sampler.checkSamplingEligibility(blockToSample.getMessage())).thenReturn(REQUIRED);
    when(sampler.checkSamplingEligibility(blockToSkip.getMessage()))
        .thenReturn(NOT_REQUIRED_OLD_EPOCH);

    // Setup for the block that will be sampled
    when(custodyGroupCountManager.getSamplingColumnIndices()).thenReturn(List.of());
    when(sampler.checkDataAvailability(blockToSample.getSlot(), blockToSample.getRoot()))
        .thenReturn(SafeFuture.completedFuture(null));

    dasPreSampler.onNewPreImportBlocks(List.of(blockToSample, blockToSkip));

    // Verify filtering
    verify(sampler).checkSamplingEligibility(blockToSample.getMessage());
    verify(sampler).checkSamplingEligibility(blockToSkip.getMessage());

    // Verify processing for the required block
    verify(sampler).checkDataAvailability(blockToSample.getSlot(), blockToSample.getRoot());

    // Verify no processing for the skipped block
    verify(sampler, never()).checkDataAvailability(eq(blockToSkip.getSlot()), any());

    verify(sampler).flush();
  }

  @Test
  void shouldPreSampleBlockWhenRequiredAndNoColumnsInCustody() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    final List<UInt64> columnIndices = List.of(UInt64.valueOf(1), UInt64.valueOf(5));
    final DataColumnSlotAndIdentifier col1 =
        new DataColumnSlotAndIdentifier(block.getSlot(), block.getRoot(), columnIndices.get(0));
    final DataColumnSlotAndIdentifier col5 =
        new DataColumnSlotAndIdentifier(block.getSlot(), block.getRoot(), columnIndices.get(1));

    when(sampler.checkSamplingEligibility(block.getMessage())).thenReturn(REQUIRED);
    when(custodyGroupCountManager.getSamplingColumnIndices()).thenReturn(columnIndices);
    when(custody.hasCustodyDataColumnSidecar(any())).thenReturn(SafeFuture.completedFuture(false));
    when(sampler.checkDataAvailability(block.getSlot(), block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(null));

    dasPreSampler.onNewPreImportBlocks(List.of(block));

    verify(custody).hasCustodyDataColumnSidecar(col1);
    verify(custody).hasCustodyDataColumnSidecar(col5);
    verify(sampler, never()).onAlreadyKnownDataColumn(any(), any());
    verify(sampler).checkDataAvailability(block.getSlot(), block.getRoot());
    verify(sampler).flush();
  }

  @Test
  void shouldNotifySamplerOfColumnsAlreadyInCustody() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    final List<UInt64> columnIndices = List.of(UInt64.valueOf(2), UInt64.valueOf(4));
    final DataColumnSlotAndIdentifier col2 =
        new DataColumnSlotAndIdentifier(block.getSlot(), block.getRoot(), columnIndices.get(0));
    final DataColumnSlotAndIdentifier col4 =
        new DataColumnSlotAndIdentifier(block.getSlot(), block.getRoot(), columnIndices.get(1));

    when(sampler.checkSamplingEligibility(block.getMessage())).thenReturn(REQUIRED);
    when(custodyGroupCountManager.getSamplingColumnIndices()).thenReturn(columnIndices);

    // col2 is in custody, col4 is not
    when(custody.hasCustodyDataColumnSidecar(col2)).thenReturn(SafeFuture.completedFuture(true));
    when(custody.hasCustodyDataColumnSidecar(col4)).thenReturn(SafeFuture.completedFuture(false));

    when(sampler.checkDataAvailability(block.getSlot(), block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(null));

    dasPreSampler.onNewPreImportBlocks(List.of(block));

    // Verify sampler is notified about the column in custody
    verify(sampler).onAlreadyKnownDataColumn(col2, RemoteOrigin.CUSTODY);
    verify(sampler, never())
        .onAlreadyKnownDataColumn(argThat(id -> id.columnIndex().equals(UInt64.valueOf(4))), any());

    verify(sampler).checkDataAvailability(block.getSlot(), block.getRoot());
    verify(sampler).flush();
  }
}
