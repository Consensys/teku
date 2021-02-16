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

package tech.pegasys.teku.reference.phase0.epoch_processing;

import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;

public interface EpochProcessingExecutor {
  void processSlashings(MutableBeaconState state);

  void processRegistryUpdates(MutableBeaconState state) throws EpochProcessingException;

  void processFinalUpdates(MutableBeaconState state);

  void processRewardsAndPenalties(MutableBeaconState state) throws EpochProcessingException;

  void processJustificationAndFinalization(MutableBeaconState state)
      throws EpochProcessingException;
}
