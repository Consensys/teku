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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes8Deserializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.spec.executionlayer.UpdatePayloadWithInclusionListResponse;

public class UpdatePayloadWithInclusionListV1Response {

  @JsonDeserialize(using = Bytes8Deserializer.class)
  private final Bytes8 payloadId;

  @JsonCreator
  public static UpdatePayloadWithInclusionListV1Response fromString(final String payloadId) {
    return new UpdatePayloadWithInclusionListV1Response(
        payloadId == null ? null : Bytes8.fromHexString(payloadId));
  }

  public UpdatePayloadWithInclusionListV1Response(final Bytes8 payloadId) {
    this.payloadId = payloadId;
  }

  public UpdatePayloadWithInclusionListResponse asInternalUpdatePayloadWithInclusionListResponse() {
    return new UpdatePayloadWithInclusionListResponse(Optional.ofNullable(payloadId));
  }

  public Bytes8 getPayloadId() {
    return payloadId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UpdatePayloadWithInclusionListV1Response that =
        (UpdatePayloadWithInclusionListV1Response) o;
    return Objects.equals(payloadId, that.payloadId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(payloadId);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("payloadId", payloadId).toString();
  }
}
