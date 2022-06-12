/*
 * Copyright ConsenSys Software Inc., 2022
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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.api.schema.altair.SignedBeaconBlockAltair;
import tech.pegasys.teku.api.schema.bellatrix.SignedBeaconBlockBellatrix;
import tech.pegasys.teku.api.schema.interfaces.SignedBlock;
import tech.pegasys.teku.api.schema.phase0.SignedBeaconBlockPhase0;

@SuppressWarnings("JavaCase")
public class GetBlockResponseV2 {
  private final Version version;

  @JsonProperty("execution_optimistic")
  public final boolean execution_optimistic;

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "version")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = SignedBeaconBlockPhase0.class, name = "phase0"),
    @JsonSubTypes.Type(value = SignedBeaconBlockAltair.class, name = "altair"),
    @JsonSubTypes.Type(value = SignedBeaconBlockBellatrix.class, name = "bellatrix")
  })
  public final SignedBlock data;

  public Version getVersion() {
    return version;
  }

  public SignedBlock getData() {
    return data;
  }

  @JsonCreator
  public GetBlockResponseV2(
      @JsonProperty("version") final Version version,
      @JsonProperty("execution_optimistic") final boolean executionOptimistic,
      @JsonProperty("data") final SignedBeaconBlock data) {
    this.version = version;
    this.execution_optimistic = executionOptimistic;
    this.data = data;
  }
}
