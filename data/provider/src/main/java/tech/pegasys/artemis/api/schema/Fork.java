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

package tech.pegasys.artemis.api.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.ssz.SSZTypes.Bytes4;

public class Fork {
  public Bytes4 previous_version;
  public Bytes4 current_version;
  public UnsignedLong epoch;

  @JsonCreator
  public Fork(
      @JsonProperty("previous_version") final Bytes4 previous_version,
      @JsonProperty("current_version") final Bytes4 current_version,
      @JsonProperty("epoch") final UnsignedLong epoch) {
    this.previous_version = previous_version;
    this.current_version = current_version;
    this.epoch = epoch;
  }

  public Fork(final tech.pegasys.artemis.datastructures.state.Fork fork) {
    this.previous_version = fork.getPrevious_version();
    this.current_version = fork.getCurrent_version();
    this.epoch = fork.getEpoch();
  }
}
