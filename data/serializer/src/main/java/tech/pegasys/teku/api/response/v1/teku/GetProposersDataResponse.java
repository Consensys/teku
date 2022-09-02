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
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings("JavaCase")
public class GetProposersDataResponse {
  @Schema(implementation = ProposerDataSchema.class)
  private final Map<String, Object> data;

  @JsonCreator
  public GetProposersDataResponse(@JsonProperty("data") final Map<String, Object> proposers_data) {
    this.data = proposers_data;
  }

  public Map<String, Object> getData() {
    return Collections.unmodifiableMap(data);
  }
}
