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

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.WRITE_DOC_START_MARKER;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DatabaseNetwork {
  @JsonProperty("fork_version")
  @VisibleForTesting
  final String forkVersion;

  @JsonProperty("deposit_contract")
  @VisibleForTesting
  final String depositContract;

  @JsonCreator
  DatabaseNetwork(
      @JsonProperty("fork_version") final String forkVersion,
      @JsonProperty("deposit_contract") final String depositContract) {
    this.forkVersion = forkVersion;
    this.depositContract = depositContract;
  }

  public static DatabaseNetwork init(
      final File source, Bytes4 forkVersion, Eth1Address depositContract) throws IOException {
    final String forkVersionString = forkVersion.toHexString().toLowerCase();
    final String depositContractString = depositContract.toHexString().toLowerCase();
    final ObjectMapper objectMapper =
        new ObjectMapper(new YAMLFactory().disable(WRITE_DOC_START_MARKER));
    if (source.exists()) {
      final DatabaseNetwork databaseNetwork =
          objectMapper.readerFor(DatabaseNetwork.class).readValue(source);

      if (!forkVersionString.equals(databaseNetwork.forkVersion)) {
        throw DatabaseStorageException.unrecoverable(
            formatMessage("fork version", forkVersionString, databaseNetwork.forkVersion));
      }
      if (databaseNetwork.depositContract != null
          && !databaseNetwork.depositContract.equals(depositContractString)) {
        throw DatabaseStorageException.unrecoverable(
            formatMessage(
                "deposit contract", depositContractString, databaseNetwork.depositContract));
      }
      return databaseNetwork;
    } else {
      DatabaseNetwork databaseNetwork =
          new DatabaseNetwork(forkVersionString, depositContractString);
      objectMapper.writerFor(DatabaseNetwork.class).writeValue(source, databaseNetwork);
      return databaseNetwork;
    }
  }

  private static String formatMessage(String fieldName, String expected, String actual) {
    return String.format(
        "Supplied %s (%s) does not match the stored database (%s). "
            + "Check that the existing database matches the current network settings.",
        fieldName, expected, actual);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final DatabaseNetwork that = (DatabaseNetwork) o;
    return Objects.equals(forkVersion, that.forkVersion)
        && Objects.equals(depositContract, that.depositContract);
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkVersion, depositContract);
  }
}
