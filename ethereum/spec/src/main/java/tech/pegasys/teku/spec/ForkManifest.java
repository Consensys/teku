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

package tech.pegasys.teku.spec;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class ForkManifest {
  private final List<Fork> forkSchedule;

  public ForkManifest(SpecConstants constants) {
    forkSchedule = new ArrayList<>();
    final Bytes4 genesisForkVersion = constants.getGenesisForkVersion();
    final UInt64 genesisEpoch = UInt64.valueOf(constants.getGenesisEpoch());
    forkSchedule.add(new Fork(genesisForkVersion, genesisForkVersion, genesisEpoch));
  }

  @VisibleForTesting
  void addFork(final Fork fork) {
    final Fork lastFork = forkSchedule.get(forkSchedule.size() - 1);
    checkArgument(fork.getEpoch().isGreaterThan(lastFork.getEpoch()));
    forkSchedule.add(fork);
  }

  public Fork get(final UInt64 epoch) {
    return forkSchedule.stream()
        .filter(fork -> fork.getEpoch().isLessThanOrEqualTo(epoch))
        .max(Comparator.comparing(Fork::getEpoch))
        .orElse(getGenesisFork());
  }

  public Fork getGenesisFork() {
    return forkSchedule.get(0);
  }

  public List<Fork> getForkSchedule() {
    return forkSchedule;
  }
}
