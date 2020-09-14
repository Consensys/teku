/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.util.config.Constants;

public class GetMetadataIntegrationTest {
  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private Eth2Network network1;
  private Eth2Network network2;
  private Eth2Peer peer1;

  @BeforeEach
  public void setUp() throws Exception {
    network1 = networkFactory.builder().startNetwork();
    network2 = networkFactory.builder().peer(network1).startNetwork();
    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void testCorrectMetadataSent() throws Exception {
    MetadataMessage md1 = peer1.requestMetadata().get(10, TimeUnit.SECONDS);
    MetadataMessage md2 = peer1.requestMetadata().get(10, TimeUnit.SECONDS);

    assertThat(md1.getSeqNumber()).isEqualTo(md2.getSeqNumber());
    assertThat(md1.getAttnets().getSize()).isEqualTo(Constants.ATTESTATION_SUBNET_COUNT);
    assertThat(md1.getAttnets().getBitCount()).isEqualTo(0);
    network1.setLongTermAttestationSubnetSubscriptions(List.of(0, 1, 8));

    MetadataMessage md3 = peer1.requestMetadata().get(10, TimeUnit.SECONDS);
    assertThat(md3.getSeqNumber()).isGreaterThan(md2.getSeqNumber());
    assertThat(md3.getAttnets().getBitCount()).isEqualTo(3);
    assertThat(md3.getAttnets().getBit(0)).isTrue();
    assertThat(md3.getAttnets().getBit(1)).isTrue();
    assertThat(md3.getAttnets().getBit(8)).isTrue();
  }
}
