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

package tech.pegasys.teku.validator.coordinator.performance;

import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class NoOpPerformanceTracker implements PerformanceTracker {

  @Override
  public void start(UInt64 nodeStartSlot) {}

  @Override
  public void saveProducedAttestation(Attestation attestation) {}

  @Override
  public void saveProducedBlock(SignedBeaconBlock block) {}

  @Override
  public void reportBlockProductionAttempt(UInt64 epoch) {}

  @Override
  public void onSlot(UInt64 slot) {}
}
