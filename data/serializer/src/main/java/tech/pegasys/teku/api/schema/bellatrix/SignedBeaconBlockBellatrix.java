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

package tech.pegasys.teku.api.schema.bellatrix;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.interfaces.SignedBlock;

public class SignedBeaconBlockBellatrix extends SignedBeaconBlock implements SignedBlock {
  private final BeaconBlockBellatrix message;

  public SignedBeaconBlockBellatrix(
      tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock) {
    super(internalBlock);
    this.message = new BeaconBlockBellatrix(internalBlock.getMessage());
  }

  @Override
  public BeaconBlockBellatrix getMessage() {
    return message;
  }

  @JsonCreator
  public SignedBeaconBlockBellatrix(
      @JsonProperty("message") final BeaconBlockBellatrix message,
      @JsonProperty("signature") final BLSSignature signature) {
    super(message, signature);
    this.message = message;
  }
}
