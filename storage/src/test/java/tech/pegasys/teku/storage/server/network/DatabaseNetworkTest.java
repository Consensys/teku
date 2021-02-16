/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

public class DatabaseNetworkTest {
  DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  public void shouldCreateNetworkFile(@TempDir final File tempDir) throws IOException {
    final File networkFile = new File(tempDir, "network.yml");
    assertThat(networkFile).doesNotExist();
    final Bytes4 fork = dataStructureUtil.randomFork().getCurrent_version();
    final Optional<Eth1Address> eth1Address = Optional.of(dataStructureUtil.randomEth1Address());
    assertThat(DatabaseNetwork.init(networkFile, fork, eth1Address))
        .isEqualTo(
            new DatabaseNetwork(
                fork.toHexString().toLowerCase(),
                eth1Address.orElseThrow().toHexString().toLowerCase()));
    assertThat(networkFile).exists();
  }

  @Test
  public void shouldTolerateMissingDepositContract(@TempDir final File tempDir) throws IOException {
    final File networkFile = new File(tempDir, "network.yml");
    assertThat(networkFile).doesNotExist();
    final Bytes4 fork = dataStructureUtil.randomFork().getCurrent_version();
    final Optional<Eth1Address> eth1Address = Optional.empty();
    assertThat(DatabaseNetwork.init(networkFile, fork, eth1Address))
        .isEqualTo(new DatabaseNetwork(fork.toHexString().toLowerCase(), ""));
    assertThat(networkFile).exists();
  }

  @Test
  public void shouldThrowIfForkDiffers(@TempDir final File tempDir) throws IOException {
    final File networkFile = new File(tempDir, "network.yml");
    assertThat(networkFile).doesNotExist();
    final Bytes4 fork = dataStructureUtil.randomFork().getCurrent_version();
    final Optional<Eth1Address> eth1Address = Optional.of(dataStructureUtil.randomEth1Address());
    DatabaseNetwork.init(
        networkFile, dataStructureUtil.randomFork().getCurrent_version(), eth1Address);

    assertThatThrownBy(() -> DatabaseNetwork.init(networkFile, fork, eth1Address))
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageStartingWith("Supplied fork version");
  }

  @Test
  public void shouldThrowIfDepositContractDiffers(@TempDir final File tempDir) throws IOException {
    final File networkFile = new File(tempDir, "network.yml");
    assertThat(networkFile).doesNotExist();
    final Bytes4 fork = dataStructureUtil.randomFork().getCurrent_version();
    final Optional<Eth1Address> eth1Address = Optional.of(dataStructureUtil.randomEth1Address());
    DatabaseNetwork.init(networkFile, fork, Optional.of(dataStructureUtil.randomEth1Address()));

    assertThatThrownBy(() -> DatabaseNetwork.init(networkFile, fork, eth1Address))
        .isInstanceOf(DatabaseStorageException.class)
        .hasMessageStartingWith("Supplied deposit contract");
  }

  @Test
  public void shouldNotThrowIfForkAndContractMatch(@TempDir final File tempDir) throws IOException {
    final File networkFile = new File(tempDir, "network.yml");
    assertThat(networkFile).doesNotExist();
    final Bytes4 fork = dataStructureUtil.randomFork().getCurrent_version();
    final Optional<Eth1Address> eth1Address = Optional.of(dataStructureUtil.randomEth1Address());
    DatabaseNetwork.init(networkFile, fork, eth1Address);

    assertDoesNotThrow(() -> DatabaseNetwork.init(networkFile, fork, eth1Address));
  }
}
