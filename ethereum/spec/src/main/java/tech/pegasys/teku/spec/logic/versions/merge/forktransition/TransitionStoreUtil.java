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

package tech.pegasys.teku.spec.logic.versions.merge.forktransition;

import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.units.bigints.UInt256s;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.forkchoice.TransitionStore;
import tech.pegasys.teku.spec.logic.common.helpers.PowBlock;

public class TransitionStoreUtil {
  private final SpecConfigMerge config;

  public TransitionStoreUtil(SpecConfigMerge config) {
    this.config = config;
  }

  public TransitionStore getTransitionStore(PowBlock anchorPowBlock) {
    UInt256 transitionTotalDifficulty = computeTransitionTotalDifficulty(anchorPowBlock);
    return TransitionStore.create(transitionTotalDifficulty);
  }

  private UInt256 computeTransitionTotalDifficulty(PowBlock anchorPowBlock) {
    UInt64 secondsPerVotingPeriod =
        UInt64.valueOf(config.getEpochsPerEth1VotingPeriod())
            .times(config.getSlotsPerEpoch())
            .times(config.getSecondsPerSlot());
    UInt64 powBlocksPerVotingPeriod =
        secondsPerVotingPeriod.dividedBy(config.getSecondsPerEth1Block());
    UInt64 powBlocksToMerge =
        config.getTargetSecondsToMerge().dividedBy(config.getSecondsPerEth1Block());
    UInt64 powBlocksAfterAnchorBlock =
        config.getEth1FollowDistance().plus(powBlocksPerVotingPeriod).plus(powBlocksToMerge);
    UInt256 anchorDifficulty =
        UInt256s.max(config.getMinAnchorPowBlockDifficulty(), anchorPowBlock.difficulty);

    return anchorPowBlock.totalDifficulty.plus(
        anchorDifficulty.multiply(powBlocksAfterAnchorBlock.longValue()));
  }
}
