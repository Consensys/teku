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

package tech.pegasys.teku.data.slashinginterchange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignedBlock {
  @JsonProperty("slot")
  public final UInt64 slot;

  @JsonProperty("signing_root")
  public final Bytes32 signingRoot;

  @JsonCreator
  public SignedBlock(
      @JsonProperty("slot") final UInt64 slot,
      @JsonProperty("signing_root") final Bytes32 signingRoot) {
    this.slot = slot;
    this.signingRoot = signingRoot;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", slot)
        .add("signingRoot", signingRoot)
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SignedBlock that = (SignedBlock) o;
    return Objects.equals(slot, that.slot) && Objects.equals(signingRoot, that.signingRoot);
  }

  public UInt64 getSlot() {
    return slot;
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, signingRoot);
  }
}
