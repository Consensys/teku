/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;

public class VoluntaryExitEvent extends Event<VoluntaryExitEvent.VoluntaryExitData> {

  private static final SerializableTypeDefinition<VoluntaryExit> VOL_EXIT_TYPE =
      SerializableTypeDefinition.object(VoluntaryExit.class)
          .withField("epoch", UINT64_TYPE, VoluntaryExit::getEpoch)
          .withField("validator_index", UINT64_TYPE, VoluntaryExit::getValidatorIndex)
          .build();

  private static final SerializableTypeDefinition<VoluntaryExitData> EVENT_TYPE =
      SerializableTypeDefinition.object(VoluntaryExitData.class)
          .withField("message", VOL_EXIT_TYPE, VoluntaryExitData::getMessage)
          .withField("signature", BYTES_TYPE, data -> data.getSignature().toBytesCompressed())
          .build();

  VoluntaryExitEvent(final SignedVoluntaryExit exit) {
    super(EVENT_TYPE, new VoluntaryExitData(exit.getMessage(), exit.getSignature()));
  }

  public static class VoluntaryExitData {
    private final VoluntaryExit message;
    private final BLSSignature signature;

    VoluntaryExitData(final VoluntaryExit message, final BLSSignature signature) {
      this.message = message;
      this.signature = signature;
    }

    private VoluntaryExit getMessage() {
      return message;
    }

    private BLSSignature getSignature() {
      return signature;
    }
  }
}
