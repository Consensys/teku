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

package tech.pegasys.teku.test.acceptance.dsl;

import static java.lang.Boolean.FALSE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import tech.pegasys.teku.util.Waiter;

public class TekuDepositSender extends Node {
  private static final Logger LOG = LogManager.getLogger();
  private static final String ENCRYPTED_KEYSTORE_ENABLED = FALSE.toString();

  public TekuDepositSender(final Network network) {
    super(network, TekuNode.TEKU_DOCKER_IMAGE, LOG);
  }

  public String sendValidatorDeposits(final BesuNode eth1Node, final int numberOfValidators) {
    container.setCommand(
        "validator",
        "generate-and-register",
        "--network",
        "minimal",
        "--Xconfirm-enabled",
        "false",
        "--encrypted-keystore-enabled",
        ENCRYPTED_KEYSTORE_ENABLED,
        "--eth1-deposit-contract-address",
        eth1Node.getDepositContractAddress(),
        "--number-of-validators",
        Integer.toString(numberOfValidators),
        "--eth1-private-key",
        eth1Node.getRichBenefactorKey(),
        "--eth1-endpoint",
        eth1Node.getInternalJsonRpcUrl());
    final StringBuilder validatorKeys = new StringBuilder();
    container.withLogConsumer(outputFrame -> validatorKeys.append(outputFrame.getUtf8String()));
    container.start();
    // Deposit sender waits for up to 2 minutes for all transactions to process
    Waiter.waitFor(() -> assertThat(container.isRunning()).isFalse(), 2, TimeUnit.MINUTES);
    container.stop();
    return validatorKeys.toString();
  }
}
