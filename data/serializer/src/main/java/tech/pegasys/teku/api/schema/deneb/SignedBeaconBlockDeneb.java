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

package tech.pegasys.teku.api.schema.deneb;

import static tech.pegasys.teku.api.schema.BLSSignature.BLS_SIGNATURE_TYPE;
import static tech.pegasys.teku.api.schema.deneb.BeaconBlockDeneb.BEACON_BLOCK_DENEB_TYPE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.interfaces.SignedBlock;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class SignedBeaconBlockDeneb extends SignedBeaconBlock implements SignedBlock {
  private BeaconBlockDeneb message;

  public static final DeserializableTypeDefinition<SignedBeaconBlockDeneb>
      SIGNED_BEACON_BLOCK_DENEB_TYPE =
          DeserializableTypeDefinition.object(SignedBeaconBlockDeneb.class)
              .name("SignedBeaconBlock")
              .initializer(SignedBeaconBlockDeneb::new)
              .withField(
                  "message",
                  BEACON_BLOCK_DENEB_TYPE,
                  SignedBeaconBlockDeneb::getMessage,
                  SignedBeaconBlockDeneb::setMessage)
              .withField(
                  "signature",
                  BLS_SIGNATURE_TYPE,
                  SignedBeaconBlockDeneb::getSignature,
                  SignedBeaconBlockDeneb::setSignature)
              .build();

  public SignedBeaconBlockDeneb(
      final tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock) {
    super(internalBlock);
    this.message = new BeaconBlockDeneb(internalBlock.getMessage());
  }

  public SignedBeaconBlockDeneb() {}

  @Override
  public BeaconBlockDeneb getMessage() {
    return message;
  }

  @Override
  public void setMessage(BeaconBlock message) {
    this.message = (BeaconBlockDeneb) message;
  }

  @JsonCreator
  public SignedBeaconBlockDeneb(
      @JsonProperty("message") final BeaconBlockDeneb message,
      @JsonProperty("signature") final BLSSignature signature) {
    super(message, signature);
    this.message = message;
  }
}
