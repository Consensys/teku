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

package tech.pegasys.teku.api.response.v1.beacon;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.altair.SignedBeaconBlockAltair;
import tech.pegasys.teku.api.schema.interfaces.SignedBlock;
import tech.pegasys.teku.api.schema.phase0.SignedBeaconBlockPhase0;

public class GetBlockResponse {
  @JsonIgnore public final SignedBeaconBlock data;

  @Schema(oneOf = {SignedBeaconBlockPhase0.class, SignedBeaconBlockAltair.class})
  public SignedBlock getData() {
    return data;
  }

  @JsonCreator
  public GetBlockResponse(@JsonProperty("data") final SignedBeaconBlock data) {
    this.data = data;
  }
}
