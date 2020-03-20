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

package tech.pegasys.artemis.api;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.api.schema.Attestation;
import tech.pegasys.artemis.api.schema.BLSSignature;
import tech.pegasys.artemis.api.schema.BeaconBlock;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class ValidatorDataProvider {
  private volatile ValidatorCoordinator validatorCoordinator;
  private static final Logger LOG = LogManager.getLogger();

  public ValidatorDataProvider(ValidatorCoordinator validatorCoordinator) {
    this.validatorCoordinator = validatorCoordinator;
  }

  public Optional<BeaconBlock> getUnsignedBeaconBlockAtSlot(UnsignedLong slot, BLSSignature randao)
      throws DataProviderException {
    if (slot == null) {
      throw new IllegalArgumentException("no slot provided.");
    }
    if (randao == null) {
      throw new IllegalArgumentException("no randao_reveal provided.");
    }

    try {
      Optional<tech.pegasys.artemis.datastructures.blocks.BeaconBlock> newBlock =
          validatorCoordinator.createUnsignedBlock(
              slot, tech.pegasys.artemis.util.bls.BLSSignature.fromBytes(randao.getBytes()));
      if (newBlock.isPresent()) {
        return Optional.of(new BeaconBlock(newBlock.get()));
      }
    } catch (Exception ex) {
      LOG.error("Failed to generate a new unsigned block", ex);
      throw new DataProviderException(ex.getMessage());
    }
    return Optional.empty();
  }

  public void submitAttestation(Attestation attestation) {
    validatorCoordinator.postSignedAttestation(attestation.asInternalAttestation(), true);
  }
}
