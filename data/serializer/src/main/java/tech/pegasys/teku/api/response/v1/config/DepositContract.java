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

package tech.pegasys.teku.api.response.v1.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositContract {
  public final UInt64 chain_id;
  public final String address;

  @JsonCreator
  public DepositContract(
      @JsonProperty("chain_id") final UInt64 chain_id,
      @JsonProperty("address") final String address) {
    this.chain_id = chain_id;
    this.address = address;
  }
}
