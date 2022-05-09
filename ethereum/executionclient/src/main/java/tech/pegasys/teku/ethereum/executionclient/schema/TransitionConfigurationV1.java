/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionclient.schema;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionlayer.TransitionConfiguration;

public class TransitionConfigurationV1 {
  @JsonSerialize(using = UInt256AsHexSerializer.class)
  @JsonDeserialize(using = UInt256AsHexDeserializer.class)
  public final UInt256 terminalTotalDifficulty;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 terminalBlockHash;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 terminalBlockNumber;

  @JsonCreator
  public TransitionConfigurationV1(
      @JsonProperty("terminalTotalDifficulty") UInt256 terminalTotalDifficulty,
      @JsonProperty("terminalBlockHash") Bytes32 terminalBlockHash,
      @JsonProperty("terminalBlockNumber") UInt64 terminalBlockNumber) {
    checkNotNull(terminalTotalDifficulty, "terminalTotalDifficulty is required");
    checkNotNull(terminalBlockHash, "terminalBlockHash is required");
    checkNotNull(terminalBlockNumber, "terminalBlockNumber is required");

    this.terminalTotalDifficulty = terminalTotalDifficulty;
    this.terminalBlockHash = terminalBlockHash;
    this.terminalBlockNumber = terminalBlockNumber;
  }

  public TransitionConfiguration asInternalTransitionConfiguration() {
    return new TransitionConfiguration(
        terminalTotalDifficulty, terminalBlockHash, terminalBlockNumber);
  }

  public static TransitionConfigurationV1 fromInternalTransitionConfiguration(
      TransitionConfiguration transitionConfiguration) {
    return new TransitionConfigurationV1(
        transitionConfiguration.getTerminalTotalDifficulty(),
        transitionConfiguration.getTerminalBlockHash(),
        transitionConfiguration.getTerminalBlockNumber());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TransitionConfigurationV1 that = (TransitionConfigurationV1) o;
    return Objects.equals(terminalTotalDifficulty, that.terminalTotalDifficulty)
        && Objects.equals(terminalBlockHash, that.terminalBlockHash)
        && Objects.equals(terminalBlockNumber, that.terminalBlockNumber);
  }

  @Override
  public int hashCode() {
    return Objects.hash(terminalTotalDifficulty, terminalBlockHash, terminalBlockNumber);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("terminalTotalDifficulty", terminalTotalDifficulty)
        .add("terminalBlockHash", terminalBlockHash)
        .add("terminalBlockNumber", terminalBlockNumber)
        .toString();
  }
}
