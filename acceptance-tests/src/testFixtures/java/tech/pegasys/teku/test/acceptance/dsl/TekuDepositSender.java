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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import tech.pegasys.teku.infrastructure.async.Waiter;

public class TekuDepositSender extends Node {
  private static final Logger LOG = LogManager.getLogger();

  public TekuDepositSender(final Network network) {
    super(network, TekuNode.TEKU_DOCKER_IMAGE, LOG);
  }

  public void sendValidatorDeposits(final BesuNode eth1Node, final int numberOfValidators) {
    container.setEnv(List.of("KEYSTORE_PASSWORD=p4ssword"));
    container.setCommand(
        "validator",
        "generate-and-register",
        "--network",
        "swift",
        "--verbose-output-enabled",
        "true",
        "--encrypted-keystore-validator-password-env",
        "KEYSTORE_PASSWORD",
        "--encrypted-keystore-withdrawal-password-env",
        "KEYSTORE_PASSWORD",
        "--eth1-deposit-contract-address",
        eth1Node.getDepositContractAddress(),
        "--number-of-validators",
        Integer.toString(numberOfValidators),
        "--eth1-private-key",
        eth1Node.getRichBenefactorKey(),
        "--eth1-endpoint",
        eth1Node.getInternalJsonRpcUrl());
    container.start();
    // Deposit sender waits for up to 2 minutes for all transactions to process
    Waiter.waitFor(() -> assertThat(container.isRunning()).isFalse(), 2, TimeUnit.MINUTES);
    container.stop();
  }
}
