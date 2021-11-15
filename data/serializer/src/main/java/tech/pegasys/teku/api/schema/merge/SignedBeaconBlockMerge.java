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

package tech.pegasys.teku.api.schema.merge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.interfaces.SignedBlock;

public class SignedBeaconBlockMerge extends SignedBeaconBlock implements SignedBlock {
  private final BeaconBlockMerge message;

  public SignedBeaconBlockMerge(
      tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock) {
    super(internalBlock);
    this.message = new BeaconBlockMerge(internalBlock.getMessage());
  }

  @Override
  public BeaconBlockMerge getMessage() {
    return message;
  }

  @JsonCreator
  public SignedBeaconBlockMerge(
      @JsonProperty("message") final BeaconBlockMerge message,
      @JsonProperty("signature") final BLSSignature signature) {
    super(message, signature);
    this.message = message;
  }
}
