/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.api.schema.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.altair.SignedBeaconBlockAltair;
import tech.pegasys.teku.api.schema.bellatrix.SignedBeaconBlockBellatrix;
import tech.pegasys.teku.api.schema.capella.SignedBeaconBlockCapella;
import tech.pegasys.teku.api.schema.deneb.SignedBeaconBlockDeneb;
import tech.pegasys.teku.api.schema.electra.SignedBeaconBlockElectra;
import tech.pegasys.teku.api.schema.phase0.SignedBeaconBlockPhase0;

@Schema(
    oneOf = {
      SignedBeaconBlockPhase0.class,
      SignedBeaconBlockAltair.class,
      SignedBeaconBlockBellatrix.class,
      SignedBeaconBlockCapella.class,
      SignedBeaconBlockDeneb.class,
      SignedBeaconBlockElectra.class
    })
public interface SignedBlock {}
