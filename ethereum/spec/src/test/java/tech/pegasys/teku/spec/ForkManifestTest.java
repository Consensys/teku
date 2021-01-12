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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

class ForkManifestTest {
  private final Bytes4 genesisForkVersion = Bytes4.fromHexString("0x00000000");
  private final Bytes4 secondForkVersion = Bytes4.fromHexString("0x11111111");
  private final Bytes4 thirdForkVersion = Bytes4.fromHexString("0x22222222");
  private final Fork genesisFork = new Fork(genesisForkVersion, genesisForkVersion, UInt64.ZERO);
  private final Fork secondFork =
      new Fork(genesisForkVersion, secondForkVersion, UInt64.valueOf(100L));
  private final Fork thirdFork =
      new Fork(secondForkVersion, thirdForkVersion, UInt64.valueOf(200L));
  private final SpecConstants constants = mock(SpecConstants.class);
  private ForkManifest forkManifest;

  @BeforeEach
  public void setup() {
    when(constants.getGenesisForkVersion()).thenReturn(genesisForkVersion);
    when(constants.getGenesisEpoch()).thenReturn(0L);
    forkManifest = new ForkManifest(constants);
    forkManifest.addFork(secondFork);
    forkManifest.addFork(thirdFork);
  }

  @Test
  void shouldNotAllowAppendingEarlierFork() {
    assertThrows(IllegalArgumentException.class, () -> forkManifest.addFork(secondFork));
  }

  @Test
  void shouldGetEpochForkAfterManifestCreation() {
    assertThat(forkManifest.get(UInt64.ZERO)).isEqualTo(genesisFork);
    assertThat(forkManifest.get(UInt64.valueOf(99L))).isEqualTo(genesisFork);
  }

  @Test
  void shouldGetGenesisForkFromManifest() {
    assertThat(forkManifest.getGenesisFork()).isEqualTo(genesisFork);
  }

  @Test
  void shouldGetMiddleFork() {
    assertThat(forkManifest.get(UInt64.valueOf(100L))).isEqualTo(secondFork);
    assertThat(forkManifest.get(UInt64.valueOf(199L))).isEqualTo(secondFork);
  }

  @Test
  void shouldGetLastForkFromSchedule() {
    assertThat(forkManifest.get(UInt64.valueOf(200L))).isEqualTo(thirdFork);
    assertThat(forkManifest.get(UInt64.MAX_VALUE)).isEqualTo(thirdFork);
  }
}
