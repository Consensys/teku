/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.api.schema.eip7732;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.interfaces.SignedBlock;

public class SignedBlindedBeaconBlockEip7732 extends SignedBeaconBlock implements SignedBlock {
  private final BlindedBlockEip7732 message;

  public SignedBlindedBeaconBlockEip7732(
      final tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock) {
    super(internalBlock);
    checkArgument(
        internalBlock.getMessage().getBody().isBlinded(), "requires a signed blinded beacon block");
    this.message = new BlindedBlockEip7732(internalBlock.getMessage());
  }

  @Override
  public BlindedBlockEip7732 getMessage() {
    return message;
  }

  @JsonCreator
  public SignedBlindedBeaconBlockEip7732(
      @JsonProperty("message") final BlindedBlockEip7732 message,
      @JsonProperty("signature") final BLSSignature signature) {
    super(message, signature);
    this.message = message;
  }
}
