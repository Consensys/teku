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

package tech.pegasys.teku.api.schema.altair;

import static tech.pegasys.teku.api.schema.BLSSignature.BLS_SIGNATURE_TYPE;
import static tech.pegasys.teku.api.schema.altair.BeaconBlockAltair.BEACON_BLOCK_ALTAIR_TYPE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.interfaces.SignedBlock;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class SignedBeaconBlockAltair extends SignedBeaconBlock implements SignedBlock {
  private BeaconBlockAltair message;

  public static final DeserializableTypeDefinition<SignedBeaconBlockAltair>
      SIGNED_BEACON_BLOCK_ALTAIR_TYPE =
          DeserializableTypeDefinition.object(SignedBeaconBlockAltair.class)
              .name("SignedBeaconBlock")
              .initializer(SignedBeaconBlockAltair::new)
              .withField(
                  "message",
                  BEACON_BLOCK_ALTAIR_TYPE,
                  SignedBeaconBlockAltair::getMessage,
                  SignedBeaconBlockAltair::setMessage)
              .withField(
                  "signature",
                  BLS_SIGNATURE_TYPE,
                  SignedBeaconBlockAltair::getSignature,
                  SignedBeaconBlockAltair::setSignature)
              .build();

  public SignedBeaconBlockAltair(
      tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock) {
    super(internalBlock);
    this.message = new BeaconBlockAltair(internalBlock.getMessage());
  }

  public SignedBeaconBlockAltair() {}

  @Override
  public BeaconBlockAltair getMessage() {
    return message;
  }

  @Override
  public void setMessage(BeaconBlock message) {
    this.message = (BeaconBlockAltair) message;
  }

  @JsonCreator
  public SignedBeaconBlockAltair(
      @JsonProperty("message") final BeaconBlockAltair message,
      @JsonProperty("signature") final BLSSignature signature) {
    super(message, signature);
    this.message = message;
  }
}
