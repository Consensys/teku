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

package tech.pegasys.teku.spec.logic.versions.altair.helpers;

import static tech.pegasys.teku.spec.constants.ParticipationFlags.indexToFlag;

import java.util.List;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.IncentivizationWeights;
import tech.pegasys.teku.spec.constants.ParticipationFlags;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class MiscHelpersAltair extends MiscHelpers {
  private static final List<FlagIndexAndWeight> flagIndicesAndWeights =
      List.of(
          new FlagIndexAndWeight(
              ParticipationFlags.TIMELY_HEAD_FLAG_INDEX, IncentivizationWeights.TIMELY_HEAD_WEIGHT),
          new FlagIndexAndWeight(
              ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX,
              IncentivizationWeights.TIMELY_SOURCE_WEIGHT),
          new FlagIndexAndWeight(
              ParticipationFlags.TIMELY_TARGET_FLAG_INDEX,
              IncentivizationWeights.TIMELY_TARGET_WEIGHT));

  public MiscHelpersAltair(final SpecConfig specConfig) {
    super(specConfig);
  }

  public List<FlagIndexAndWeight> getFlagIndicesAndWeights() {
    return flagIndicesAndWeights;
  }

  public byte addFlag(final byte participationFlags, final int flagIndex) {
    final int flag = indexToFlag(flagIndex);
    return (byte) (participationFlags | flag);
  }

  public boolean hasFlag(final byte participationFlags, final int flagIndex) {
    final int flag = indexToFlag(flagIndex);
    return (participationFlags & flag) == flag;
  }

  public static class FlagIndexAndWeight {
    final int index;
    final int weight;

    private FlagIndexAndWeight(final int index, final int weight) {
      this.index = index;
      this.weight = weight;
    }

    public int getIndex() {
      return index;
    }

    public int getWeight() {
      return weight;
    }
  }
}
