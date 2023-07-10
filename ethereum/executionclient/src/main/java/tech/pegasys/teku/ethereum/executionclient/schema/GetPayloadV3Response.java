/*
 * Copyright ConsenSys Software Inc., 2022
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexSerializer;

public class GetPayloadV3Response {
  public final ExecutionPayloadV3 executionPayload;

  @JsonSerialize(using = UInt256AsHexSerializer.class)
  @JsonDeserialize(using = UInt256AsHexDeserializer.class)
  public final UInt256 blockValue;

  public final BlobsBundleV1 blobsBundle;

  public final boolean shouldOverrideBuilder;

  public GetPayloadV3Response(
      @JsonProperty("executionPayload") final ExecutionPayloadV3 executionPayload,
      @JsonProperty("blockValue") final UInt256 blockValue,
      @JsonProperty("blobsBundle") final BlobsBundleV1 blobsBundle,
      @JsonProperty("shouldOverrideBuilder") final boolean shouldOverrideBuilder) {
    this.executionPayload = executionPayload;
    this.blockValue = blockValue;
    this.blobsBundle = blobsBundle;
    this.shouldOverrideBuilder = shouldOverrideBuilder;
  }
}
