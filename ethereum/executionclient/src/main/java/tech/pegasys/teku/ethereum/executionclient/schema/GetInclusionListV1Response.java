/*
 * Copyright Consensys Software Inc., 2024
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
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Serializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;

public record GetInclusionListV1Response(
    @JsonSerialize(contentUsing = Bytes20Serializer.class)
        @JsonDeserialize(contentUsing = Bytes20Deserializer.class)
        List<Bytes20> inclusionListSummary,
    @JsonSerialize(contentUsing = BytesSerializer.class)
        @JsonDeserialize(contentUsing = BytesDeserializer.class)
        List<Bytes> inclusionListTransactions) {
  public GetInclusionListV1Response(
      @JsonProperty("inclusionListSummary") final List<Bytes20> inclusionListSummary,
      @JsonProperty("inclusionListTransactions") final List<Bytes> inclusionListTransactions) {
    this.inclusionListSummary = inclusionListSummary;
    this.inclusionListTransactions = inclusionListTransactions;
  }
}
