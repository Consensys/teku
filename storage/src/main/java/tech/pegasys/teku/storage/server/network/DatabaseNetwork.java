/*
 * Copyright Consensys Software Inc., 2022
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

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DatabaseNetwork {
  @JsonProperty("fork_version")
  @VisibleForTesting
  final String forkVersion;

  @JsonProperty("deposit_contract")
  @VisibleForTesting
  final String depositContract;

  @JsonProperty("deposit_chain_id")
  @VisibleForTesting
  final String depositChainId; // Can be null for non-Ephemery networks

  static final Eth2NetworkConfiguration NETWORK_CONFIG =
      Eth2NetworkConfiguration.builder(Eth2Network.EPHEMERY).build();

  // Single constructor that handles both cases
  @JsonCreator
  DatabaseNetwork(
      @JsonProperty("fork_version") final String forkVersion,
      @JsonProperty("deposit_contract") final String depositContract,
      @JsonProperty("deposit_chain_id") final String depositChainId) {
    this.forkVersion = forkVersion;
    this.depositContract = depositContract;
    this.depositChainId = depositChainId; // Can be null for non-Ephemery networks
  }

  // Initialization logic
  public static DatabaseNetwork init(
      final File source,
      final Bytes4 forkVersion,
      final Eth1Address depositContract,
      final Optional<String> depositChainId)
      throws IOException {
    final String forkVersionString = forkVersion.toHexString().toLowerCase(Locale.ROOT);
    final String depositContractString = depositContract.toHexString().toLowerCase(Locale.ROOT);
    final ObjectMapper objectMapper =
        new ObjectMapper(new YAMLFactory().disable(WRITE_DOC_START_MARKER));

    if (source.exists()) {
      final DatabaseNetwork databaseNetwork =
          objectMapper.readerFor(DatabaseNetwork.class).readValue(source);

      // Fork version validation
      if (!forkVersionString.equals(databaseNetwork.forkVersion)) {
        throw DatabaseStorageException.unrecoverable(
            formatMessage("fork version", forkVersionString, databaseNetwork.forkVersion));
      }

      // Deposit contract validation
      if (databaseNetwork.depositContract != null
          && !databaseNetwork.depositContract.equals(depositContractString)) {
        throw DatabaseStorageException.unrecoverable(
            formatMessage(
                "deposit contract", depositContractString, databaseNetwork.depositContract));
      }

      if (NETWORK_CONFIG.getEth2Network().equals(Optional.of(Eth2Network.EPHEMERY))) {

        // Deposit chain ID validation
        if (depositChainId.isPresent()
            && databaseNetwork.depositChainId != null
            && !databaseNetwork.depositChainId.equals(depositChainId.get())) {
          throw DatabaseStorageException.unrecoverable(
              formatMessage(
                  "deposit chain ID",
                  depositChainId.get(),
                  String.valueOf(databaseNetwork.depositChainId)));
        }
      }
      return databaseNetwork;
    } else {
      // Handle Ephemery or other networks
      String depositChainIdString = depositChainId.orElse(null);
      DatabaseNetwork databaseNetwork;
      System.out.println(
          "network " + NETWORK_CONFIG.getEth2Network().equals(Optional.of(Eth2Network.EPHEMERY)));
      if (NETWORK_CONFIG.getEth2Network().equals(Optional.of(Eth2Network.EPHEMERY))) {
        databaseNetwork =
            new DatabaseNetwork(forkVersionString, depositContractString, depositChainIdString);
      } else {
        databaseNetwork = new DatabaseNetwork(forkVersionString, depositContractString, null);
      }

      objectMapper.writerFor(DatabaseNetwork.class).writeValue(source, databaseNetwork);
      return databaseNetwork;
    }
  }

  private static String formatMessage(
      final String fieldName, final String expected, final String actual) {
    return String.format(
        "Supplied %s (%s) does not match the stored database (%s). "
            + "Check that the existing database matches the current network settings.",
        fieldName, expected, actual);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DatabaseNetwork that = (DatabaseNetwork) o;
    return Objects.equals(forkVersion, that.forkVersion)
        && Objects.equals(depositContract, that.depositContract)
        && Objects.equals(depositChainId, that.depositChainId); // Compare deposit_chain_id
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkVersion, depositContract, depositChainId);
  }
}
