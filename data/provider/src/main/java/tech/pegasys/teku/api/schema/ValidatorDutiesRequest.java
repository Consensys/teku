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

package tech.pegasys.teku.api.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import java.util.List;

public class ValidatorDutiesRequest {
  public final UnsignedLong epoch;
  public final List<BLSPubKey> pubkeys;

  @JsonCreator
  public ValidatorDutiesRequest(
      @JsonProperty(value = "epoch", required = true) UnsignedLong epoch,
      @JsonProperty(value = "pubkeys", required = true) final List<BLSPubKey> pubkeys) {
    this.epoch = epoch;
    this.pubkeys = pubkeys;
  }
}
