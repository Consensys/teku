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

package tech.pegasys.teku.api.response.v1.teku;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.Set;
import tech.pegasys.teku.api.schema.SignedBeaconBlockWithRoot;
import tech.pegasys.teku.api.schema.Version;

public class GetAllBlocksAtSlotResponse {
  private final Version version;

  private final Set<SignedBeaconBlockWithRoot> data;

  public Set<SignedBeaconBlockWithRoot> getData() {
    return Collections.unmodifiableSet(data);
  }

  public Version getVersion() {
    return version;
  }

  @JsonCreator
  public GetAllBlocksAtSlotResponse(
      @JsonProperty("version") final Version version,
      @JsonProperty("data") final Set<SignedBeaconBlockWithRoot> data) {
    this.version = version;
    this.data = data;
  }
}
