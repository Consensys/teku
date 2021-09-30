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

package tech.pegasys.teku.services.powchain.execution.client.schema;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.powchain.execution.client.serializer.Bytes20Serializer;
import tech.pegasys.teku.services.powchain.execution.client.serializer.BytesSerializer;
import tech.pegasys.teku.services.powchain.execution.client.serializer.UInt64AsHexSerializer;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel.ConsensusValidationResult;
import tech.pegasys.teku.ssz.type.Bytes20;

public class ConsensusValidatedRequest {
  @JsonSerialize(using = BytesSerializer.class)
  public final Bytes32 blockHash;

  public final ConsensusValidationResult status;


  public ConsensusValidatedRequest(Bytes32 blockHash,
      ConsensusValidationResult status) {
    this.blockHash = blockHash;
    this.status = status;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConsensusValidatedRequest)) {
      return false;
    }
    ConsensusValidatedRequest that = (ConsensusValidatedRequest) o;
    return Objects.equal(blockHash, that.blockHash) &&
        status == that.status;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(blockHash, status);
  }

  @Override
  public String toString() {
    return "ConsensusValidatedRequest{" +
        "blockHash=" + blockHash +
        ", status=" + status +
        '}';
  }
}
