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

package tech.pegasys.teku.networking.eth2.gossip.config;

import static tech.pegasys.teku.util.DoubleAssert.assertThatDouble;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networks.ConstantsLoader;
import tech.pegasys.teku.spec.constants.SpecConstants;

public class ScoringConfigTest {

  private final SpecConstants specConstants = ConstantsLoader.loadConstants("mainnet");
  private final ScoringConfig scoringConfig = ScoringConfig.create(specConstants, 8);

  @Test
  public void maxPositiveScore() {
    assertThatDouble(scoringConfig.getMaxPositiveScore()).isApproximately(107.5);
  }
}
