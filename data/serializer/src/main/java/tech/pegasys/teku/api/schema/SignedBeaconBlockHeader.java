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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

public class SignedBeaconBlockHeader {
  public final BeaconBlockHeader message;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public final BLSSignature signature;

  public SignedBeaconBlockHeader(
      tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader signedHeader) {
    this.message = new BeaconBlockHeader(signedHeader.getMessage());
    this.signature = new BLSSignature(signedHeader.getSignature());
  }

  @JsonCreator
  public SignedBeaconBlockHeader(
      @JsonProperty("message") final BeaconBlockHeader message,
      @JsonProperty("signature") final BLSSignature signature) {
    this.message = message;
    this.signature = signature;
  }

  public tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader
      asInternalSignedBeaconBlockHeader() {
    return new tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader(
        message.asInternalBeaconBlockHeader(), signature.asInternalBLSSignature());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SignedBeaconBlockHeader that = (SignedBeaconBlockHeader) o;
    return Objects.equals(message, that.message) && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, signature);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", message)
        .add("signature", signature)
        .toString();
  }
}
