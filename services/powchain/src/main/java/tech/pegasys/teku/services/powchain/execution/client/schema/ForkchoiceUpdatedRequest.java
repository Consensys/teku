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
import tech.pegasys.teku.services.powchain.execution.client.serializer.BytesSerializer;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel.ConsensusValidationResult;

public class ForkchoiceUpdatedRequest {
  @JsonSerialize(using = BytesSerializer.class)
  public final Bytes32 headBlockHash;

  @JsonSerialize(using = BytesSerializer.class)
  public final Bytes32 finalizedBlockHash;


  public ForkchoiceUpdatedRequest(Bytes32 headBlockHash,
      Bytes32 finalizedBlockHash) {
    this.headBlockHash = headBlockHash;
    this.finalizedBlockHash = finalizedBlockHash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ForkchoiceUpdatedRequest)) {
      return false;
    }
    ForkchoiceUpdatedRequest that = (ForkchoiceUpdatedRequest) o;
    return Objects.equal(headBlockHash, that.headBlockHash) &&
        Objects.equal(finalizedBlockHash, that.finalizedBlockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(headBlockHash, finalizedBlockHash);
  }

  @Override
  public String toString() {
    return "ForkchoiceUpdatedRequest{" +
        "headBlockHash=" + headBlockHash +
        ", finalizedBlockHash=" + finalizedBlockHash +
        '}';
  }
}
