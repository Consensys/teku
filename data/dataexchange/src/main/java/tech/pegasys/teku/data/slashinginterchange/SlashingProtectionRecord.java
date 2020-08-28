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
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlashingProtectionRecord {
  @JsonProperty("lastSignedBlockSlot")
  public final UInt64 lastSignedBlockSlot;

  @JsonProperty("lastSignedAttestationSourceEpoch")
  public final UInt64 lastSignedAttestationSourceEpoch;

  @JsonProperty("lastSignedAttestationTargetEpoch")
  public final UInt64 lastSignedAttestationTargetEpoch;

  @JsonProperty("genesisValidatorsRoot")
  public final Bytes32 genesisValidatorsRoot;

  @JsonCreator
  public SlashingProtectionRecord(
      @JsonProperty("lastSignedBlockSlot") final UInt64 lastSignedBlockSlot,
      @JsonProperty("lastSignedAttestationSourceEpoch")
          final UInt64 lastSignedAttestationSourceEpoch,
      @JsonProperty("lastSignedAttestationTargetEpoch")
          final UInt64 lastSignedAttestationTargetEpoch,
      @JsonProperty("genesisValidatorsRoot") final Bytes32 genesisValidatorsRoot) {
    this.lastSignedBlockSlot = lastSignedBlockSlot;
    this.lastSignedAttestationSourceEpoch = lastSignedAttestationSourceEpoch;
    this.lastSignedAttestationTargetEpoch = lastSignedAttestationTargetEpoch;
    this.genesisValidatorsRoot = genesisValidatorsRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SlashingProtectionRecord that = (SlashingProtectionRecord) o;
    return Objects.equals(lastSignedBlockSlot, that.lastSignedBlockSlot)
        && Objects.equals(lastSignedAttestationSourceEpoch, that.lastSignedAttestationSourceEpoch)
        && Objects.equals(lastSignedAttestationTargetEpoch, that.lastSignedAttestationTargetEpoch)
        && Objects.equals(genesisValidatorsRoot, that.genesisValidatorsRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        lastSignedBlockSlot,
        lastSignedAttestationSourceEpoch,
        lastSignedAttestationTargetEpoch,
        genesisValidatorsRoot);
  }
}
