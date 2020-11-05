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
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

public class SignedBeaconBlock {
  public final BeaconBlock message;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public final BLSSignature signature;

  public SignedBeaconBlock(
      tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock internalBlock) {
    this.signature = new BLSSignature(internalBlock.getSignature());
    this.message = new BeaconBlock(internalBlock.getMessage());
  }

  @JsonCreator
  public SignedBeaconBlock(
      @JsonProperty("message") final BeaconBlock message,
      @JsonProperty("signature") final BLSSignature signature) {
    this.message = message;
    this.signature = signature;
  }

  public tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock asInternalSignedBeaconBlock() {
    return new tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock(
        message.asInternalBeaconBlock(), signature.asInternalBLSSignature());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SignedBeaconBlock)) return false;
    SignedBeaconBlock that = (SignedBeaconBlock) o;
    return Objects.equals(message, that.message) && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, signature);
  }
}
