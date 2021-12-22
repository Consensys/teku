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

package tech.pegasys.teku.test.acceptance;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class SyncCommitteeGossipAcceptanceTest extends AcceptanceTestBase {
  private static final int NODE_VALIDATORS = 8;
  private static final int TOTAL_VALIDATORS = NODE_VALIDATORS * 2;
  private final String network = "less-swift";

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();
  private TekuNode primaryNode;
  private TekuNode secondaryNode;
  private TekuValidatorNode validatorClient;

  @BeforeEach
  public void setup() {
    final int genesisTime = timeProvider.getTimeInSeconds().plus(15).intValue();
    primaryNode =
        createTekuNode(
            config -> configureNode(config, genesisTime).withInteropValidators(0, NODE_VALIDATORS));
    secondaryNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime)
                    .withLogging("debug")
                    .withInteropValidators(0, 0)
                    .withPeers(primaryNode));
    validatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork(network)
                    .withInteropValidators(NODE_VALIDATORS, NODE_VALIDATORS)
                    .withBeaconNode(secondaryNode));
  }

  @Test
  public void shouldContainSyncCommitteeAggregates() throws Exception {
    primaryNode.start();
    primaryNode.startEventListener(List.of(EventType.contribution_and_proof));

    secondaryNode.start();
    secondaryNode.startEventListener(List.of(EventType.contribution_and_proof));
    validatorClient.start();

    primaryNode.waitForEpoch(1);

    // primary node has validators 1-7, expect gossip from aggregators 8-15 coming through
    primaryNode.waitForContributionAndProofEvent(
        proof -> proof.message.aggregatorIndex.isGreaterThanOrEqualTo(8));

    // secondary node has remote validators 8-15, expect gossip from aggregators 1-7
    secondaryNode.waitForContributionAndProofEvent(
        proof -> proof.message.aggregatorIndex.isLessThan(8));

    secondaryNode.waitForFullSyncCommitteeAggregate();
    validatorClient.stop();
    secondaryNode.stop();
    primaryNode.stop();
  }

  private TekuNode.Config configureNode(final TekuNode.Config node, final int genesisTime) {
    return node.withNetwork(network)
        .withAltairEpoch(UInt64.ZERO)
        .withGenesisTime(genesisTime)
        .withInteropNumberOfValidators(TOTAL_VALIDATORS)
        .withRealNetwork();
  }
}
