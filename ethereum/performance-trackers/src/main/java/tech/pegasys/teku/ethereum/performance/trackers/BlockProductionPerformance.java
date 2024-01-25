/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.ethereum.performance.trackers;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * This is high level flow, some steps are executed only if builder flow take place
 *
 * <pre>
 *        start
 *    |              |
 *    v              v
 * prepareOnTick  prepareApplyDeferredAttestations
 *    |              |
 *    -----     ------
 *           |
 *           v
 * preparation_process_head
 *         |
 *         v
 *       retrieve_state
 *    (which set slotTime too)
 *         |
 *         v
 *    beaconBlockPrepared
 *    |                  |
 *    v                  v
 * engineGetPayload   builderGetHeader (maybe)
 *    |                 |
 *    v                 v
 *    builderBidValidated (maybe)
 *            |
 *            v
 *    beacon_block_created
 *            |
 *            v
 *    state_transition
 *            |
 *            v
 *      state_hashing
 *            |
 *            v
 *         complete
 *
 * </pre>
 */
public interface BlockProductionPerformance {
  String COMPLETE_LABEL = "complete";

  BlockProductionPerformance NOOP =
      new BlockProductionPerformance() {
        @Override
        public void slotTime(final Supplier<UInt64> slotTimeSupplier) {}

        @Override
        public void complete() {}

        @Override
        public void prepareOnTick() {}

        @Override
        public void prepareApplyDeferredAttestations() {}

        @Override
        public void prepareProcessHead() {}

        @Override
        public void beaconBlockPrepared() {}

        @Override
        public void getStateAtSlot() {}

        @Override
        public void engineGetPayload() {}

        @Override
        public void builderGetHeader() {}

        @Override
        public void builderBidValidated() {}

        @Override
        public void beaconBlockCreated() {}

        @Override
        public void stateTransition() {}

        @Override
        public void stateHashing() {}
      };

  void slotTime(Supplier<UInt64> slotTimeSupplier);

  void complete();

  void prepareOnTick();

  void prepareApplyDeferredAttestations();

  void prepareProcessHead();

  void beaconBlockPrepared();

  void getStateAtSlot();

  void engineGetPayload();

  void builderGetHeader();

  void builderBidValidated();

  void beaconBlockCreated();

  void stateTransition();

  void stateHashing();
}
