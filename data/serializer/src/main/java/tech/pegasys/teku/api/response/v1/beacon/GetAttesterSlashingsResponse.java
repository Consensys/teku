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
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tech.pegasys.teku.api.schema.AttesterSlashing;

public class GetAttesterSlashingsResponse {
  @JsonProperty("data")
  public final List<AttesterSlashing> data;

  @JsonCreator
  public GetAttesterSlashingsResponse(@JsonProperty("data") final List<AttesterSlashing> data) {
    this.data = data;
  }
}
