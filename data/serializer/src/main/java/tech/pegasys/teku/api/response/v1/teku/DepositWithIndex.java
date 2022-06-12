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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.DepositData;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositWithIndex {
  @Schema(type = "string", format = "uint64")
  public final UInt64 index;

  public final DepositData data;

  public DepositWithIndex(
      @JsonProperty("index") final UInt64 index, @JsonProperty("data") final DepositData data) {
    this.index = index;
    this.data = data;
  }
}
