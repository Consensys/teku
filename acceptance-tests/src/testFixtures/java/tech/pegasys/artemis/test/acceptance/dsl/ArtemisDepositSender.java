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

import static java.lang.Boolean.FALSE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import tech.pegasys.artemis.test.acceptance.dsl.docker.TestContainerMountableFolder;
import tech.pegasys.artemis.util.Waiter;

public class ArtemisDepositSender extends Node {
  private static final Logger LOG = LogManager.getLogger();
  private static final String ENCRYPTED_KEYSTORE_ENABLED = FALSE.toString();
  private static final UnsignedLong MINIMUM_REQUIRED_GWEI = UnsignedLong.valueOf(32_000_000_000L);
  private static final String CONTAINER_KEYS_DIRECTORY = "/tmp/keys";
  private static final String CONTAINER_KEYS_YAML = "keys.yaml";

  public ArtemisDepositSender(final Network network) {
    super(network, ArtemisNode.ARTEMIS_DOCKER_IMAGE, LOG);
  }

  public String sendValidatorDeposits(final BesuNode eth1Node, final int numberOfValidators) {
    final TestContainerMountableFolder testContainerMountableFolder =
        new TestContainerMountableFolder();
    final Path tempDirectory = testContainerMountableFolder.createTempDirectory();

    container.withFileSystemBind(tempDirectory.toString(), CONTAINER_KEYS_DIRECTORY);

    container.setCommand(
        "validator",
        "generate",
        "--X-confirm-enabled",
        "false",
        "--keys-output-path",
        CONTAINER_KEYS_DIRECTORY + "/" + CONTAINER_KEYS_YAML,
        "--deposit-amount-gwei",
        MINIMUM_REQUIRED_GWEI.toString(),
        "--encrypted-keystore-enabled",
        ENCRYPTED_KEYSTORE_ENABLED,
        "--eth1-deposit-contract-address",
        eth1Node.getDepositContractAddress(),
        "--X-number-of-validators",
        Integer.toString(numberOfValidators),
        "--eth1-private-key",
        eth1Node.getRichBenefactorKey(),
        "--eth1-endpoint",
        eth1Node.getInternalJsonRpcUrl());
    container.start();
    Waiter.waitFor(() -> assertThat(container.isRunning()).isFalse());
    container.stop();
    try {
      return Files.readString(tempDirectory.resolve(CONTAINER_KEYS_YAML));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
