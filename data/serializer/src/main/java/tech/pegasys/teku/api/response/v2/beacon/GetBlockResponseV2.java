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

package tech.pegasys.teku.api.response.v2.beacon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.altair.SignedBeaconBlockAltair;
import tech.pegasys.teku.api.schema.interfaces.VersionedSignedBeaconBlock;
import tech.pegasys.teku.api.schema.phase0.SignedBeaconBlockPhase0;
import tech.pegasys.teku.spec.SpecMilestone;

public class GetBlockResponseV2 {
  private final SpecMilestone version;
  private final VersionedSignedBeaconBlock data;

  public SpecMilestone getVersion() {
    return version;
  }

  @Schema(oneOf = {SignedBeaconBlockPhase0.class, SignedBeaconBlockAltair.class})
  public VersionedSignedBeaconBlock getData() {
    return data;
  }

  @JsonCreator
  public GetBlockResponseV2(
      @JsonProperty("version") final SpecMilestone version,
      @JsonProperty("data") final VersionedSignedBeaconBlock data) {
    this.version = version;
    this.data = data;
  }
}
