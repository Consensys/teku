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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class MultiNodeAltairAcceptanceTest extends AcceptanceTestBase {
  static final int NODE_VALIDATORS = 8;

  private final SystemTimeProvider timeProvider = new SystemTimeProvider();
  private TekuNode primaryNode;
  private TekuNode secondaryNode;
  private TekuValidatorNode validatorClient;

  @BeforeEach
  public void setup() {
    final int genesisTime = timeProvider.getTimeInSeconds().plus(5).intValue();
    primaryNode =
        createTekuNode(
            config -> configureNode(config, genesisTime).withInteropValidators(0, NODE_VALIDATORS));
    secondaryNode =
        createTekuNode(
            config ->
                configureNode(config, genesisTime)
                    .withInteropValidators(0, 0)
                    .withPeers(primaryNode));
    validatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork("minimal")
                    .withAltairEpoch(UInt64.ZERO)
                    .withInteropValidators(NODE_VALIDATORS, NODE_VALIDATORS)
                    .withBeaconNode(secondaryNode));
  }

  @Test
  public void shouldContainSyncCommitteeAggregates() throws Exception {
    primaryNode.start();
    secondaryNode.start();
    validatorClient.start();
    secondaryNode.waitForFullSyncCommitteeAggregate();
  }

  private TekuNode.Config configureNode(final TekuNode.Config node, final int genesisTime) {
    return node.withNetwork("minimal")
        .withAltairEpoch(UInt64.ZERO)
        .withGenesisTime(genesisTime)
        .withInteropNumberOfValidators(NODE_VALIDATORS)
        .withRealNetwork();
  }
}
