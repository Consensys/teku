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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PreparePayloadResponse {
  private UInt64 payloadId;

  public PreparePayloadResponse(@JsonProperty("payloadId") UInt64 payloadId) {
    this.payloadId = payloadId;
  }

  public UInt64 getPayloadId() {
    return payloadId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PreparePayloadResponse that = (PreparePayloadResponse) o;
    return Objects.equals(payloadId, that.payloadId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(payloadId);
  }

  @Override
  public String toString() {
    return "PreparePayloadResponse{" + "payloadId=" + payloadId + '}';
  }
}
