/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.networking.p2p.libp2p.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.libp2p.pubsub.gossip.GossipParams;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;

public class LibP2PParamsFactoryTest {

  @Test
  void createGossipParams_checkZeroDsSucceed() {
    GossipConfig gossipConfig = GossipConfig.builder().d(0).dLow(0).dHigh(0).build();

    GossipParams gossipParams = LibP2PParamsFactory.createGossipParams(gossipConfig);

    assertThat(gossipParams.getDOut()).isEqualTo(0);
  }
}
