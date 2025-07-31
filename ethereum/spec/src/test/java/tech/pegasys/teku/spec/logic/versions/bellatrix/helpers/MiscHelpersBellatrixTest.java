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

package tech.pegasys.teku.spec.logic.versions.bellatrix.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;

class MiscHelpersBellatrixTest {
  private final UInt64 nextForkEpoch = UInt64.valueOf(1024_000);
  private final Spec spec =
      TestSpecFactory.createMinimalBellatrix(
          builder ->
              builder.bellatrixBuilder(
                  bellatrixBuilder -> bellatrixBuilder.nextForkEpoch(Optional.of(nextForkEpoch))));
  private final MiscHelpersBellatrix helpers =
      new MiscHelpersBellatrix(spec.getGenesisSpecConfig());
  final Bytes4 nextForkVersion =
      spec.forMilestone(SpecMilestone.BELLATRIX)
          .getConfig()
          .toVersionBellatrix()
          .orElseThrow()
          .getBellatrixForkVersion();

  @Test
  void canComputeForkVersion() {
    assertThat(helpers.computeForkVersion(UInt64.ZERO)).isEqualTo(nextForkVersion);
    assertThat(helpers.computeForkVersion(nextForkEpoch.decrement())).isEqualTo(nextForkVersion);
  }

  @Test
  void canDetectEpochIsNextFork() {
    assertThatThrownBy(() -> helpers.computeForkVersion(nextForkEpoch.increment()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> helpers.computeForkVersion(nextForkEpoch))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
