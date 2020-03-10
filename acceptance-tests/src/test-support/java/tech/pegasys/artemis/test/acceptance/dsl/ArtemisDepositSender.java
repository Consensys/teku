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

package tech.pegasys.artemis.test.acceptance.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import tech.pegasys.artemis.util.Waiter;

public class ArtemisDepositSender extends Node {
  private static final Logger LOG = LogManager.getLogger();

  public ArtemisDepositSender(final Network network) {
    super(network, ArtemisNode.ARTEMIS_DOCKER_IMAGE, LOG);
  }

  public String sendValidatorDeposits(final BesuNode eth1Node, final int numberOfValidators) {
    container.setCommand(
        "validator",
        "generate",
        "--encrypted-keystore-enabled",
        "false",
        "--contract-address",
        eth1Node.getDepositContractAddress(),
        "--number-of-validators",
        Integer.toString(numberOfValidators),
        "--private-key",
        eth1Node.getRichBenefactorKey(),
        "--node-url",
        eth1Node.getInternalJsonRpcUrl());
    final StringBuilder validatorKeys = new StringBuilder();
    container.withLogConsumer(outputFrame -> validatorKeys.append(outputFrame.getUtf8String()));
    container.start();
    Waiter.waitFor(() -> assertThat(container.isRunning()).isFalse());
    container.stop();
    return validatorKeys.toString();
  }
}
