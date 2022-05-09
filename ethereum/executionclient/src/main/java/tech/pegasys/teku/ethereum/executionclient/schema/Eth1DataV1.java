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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@SuppressWarnings("JavaCase")
public class Eth1DataV1 {
  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 depositRoot;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 depositCount;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 blockHash;

  public Eth1DataV1(final tech.pegasys.teku.spec.datastructures.blocks.Eth1Data eth1Data) {
    depositCount = eth1Data.getDepositCount();
    depositRoot = eth1Data.getDepositRoot();
    blockHash = eth1Data.getBlockHash();
  }

  @JsonCreator
  public Eth1DataV1(
      @JsonProperty("depositRoot") final Bytes32 depositRoot,
      @JsonProperty("depositCount") final UInt64 depositCount,
      @JsonProperty("blockHash") final Bytes32 blockHash) {
    this.depositRoot = depositRoot;
    this.depositCount = depositCount;
    this.blockHash = blockHash;
  }

  public tech.pegasys.teku.spec.datastructures.blocks.Eth1Data asInternalEth1Data() {
    return new tech.pegasys.teku.spec.datastructures.blocks.Eth1Data(
        depositRoot, depositCount, blockHash);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Eth1DataV1)) {
      return false;
    }
    Eth1DataV1 eth1Data = (Eth1DataV1) o;
    return Objects.equals(depositRoot, eth1Data.depositRoot)
        && Objects.equals(depositCount, eth1Data.depositCount)
        && Objects.equals(blockHash, eth1Data.blockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(depositRoot, depositCount, blockHash);
  }
}
