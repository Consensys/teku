/*
 * Copyright Consensys Software Inc., 2025
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
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7805.GetInclusionListResponse;

public class GetInclusionListV1Response {

  @JsonDeserialize(contentUsing = BytesDeserializer.class)
  public final List<Bytes> transactions;

  public GetInclusionListV1Response(final @JsonProperty("transactions") List<Bytes> transactions) {
    this.transactions = transactions;
  }

  public GetInclusionListResponse asInternalInclusionListResponse() {
    return new GetInclusionListResponse(transactions);
  }
}
