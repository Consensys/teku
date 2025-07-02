/*
 * Copyright Consensys Software Inc., 2024
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

import static tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED;

import java.util.Collection;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;

public class DasPreSampler {

  private static final Logger LOG = LogManager.getLogger();

  private final DataAvailabilitySampler sampler;

  public DasPreSampler(final DataAvailabilitySampler sampler) {
    this.sampler = sampler;
  }

  private boolean isSamplingRequired(final SignedBeaconBlock block) {
    return sampler.checkSamplingEligibility(block.getMessage()) == REQUIRED;
  }

  public void onNewPreImportBlocks(final Collection<SignedBeaconBlock> blocks) {
    final List<SignedBeaconBlock> blocksToSample =
        blocks.stream().filter(this::isSamplingRequired).toList();

    LOG.debug(
        "DasPreSampler: requesting pre-sample for {} (of {} received) blocks: {}",
        blocksToSample.size(),
        blocks.size(),
        StringifyUtil.toIntRangeStringWithSize(
            blocksToSample.stream().map(block -> block.getSlot().intValue()).toList()));

    blocksToSample.forEach(this::onNewPreImportBlock);
    sampler.flush();
  }

  private void onNewPreImportBlock(final SignedBeaconBlock block) {
    sampler
        .checkDataAvailability(block.getSlot(), block.getRoot())
        .finish(
            succ ->
                LOG.debug(
                    "DasPreSampler: success pre-sampling block {} ({})",
                    block.getSlot(),
                    block.getRoot()),
            err ->
                LOG.debug(
                    "DasPreSampler: error pre-sampling block {} ({}): {}",
                    block.getSlot(),
                    block.getRoot(),
                    err));
  }
}
