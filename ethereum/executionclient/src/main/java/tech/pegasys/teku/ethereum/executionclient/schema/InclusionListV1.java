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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.TransactionSchema;

public class InclusionListV1 {

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = BytesDeserializer.class)
  public final List<Bytes> inclusionList;

  public InclusionListV1(final @JsonProperty("inclusionList") List<Bytes> inclusionList) {
    this.inclusionList = inclusionList;
  }

  public List<Transaction> asInternalTransaction(final TransactionSchema transactionSchema) {
    return inclusionList.stream().map(transactionSchema::fromBytes).toList();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final InclusionListV1 that = (InclusionListV1) o;
    return Objects.equals(inclusionList, that.inclusionList);
  }

  @Override
  public int hashCode() {
    return Objects.hash(inclusionList);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add(
            "transactions",
            inclusionList.stream().map(this::bytesToBriefString).collect(Collectors.joining(", ")))
        .toString();
  }

  private String bytesToBriefString(final Bytes bytes) {
    return bytes.slice(0, 7).toUnprefixedHexString();
  }
}
