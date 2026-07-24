/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * The source beacon states the Fast Confirmation Rule reads from, retrieved once per slot.
 *
 * <p>The spec distinguishes four state sources; not all are needed every slot:
 *
 * <ul>
 *   <li>{@code previousBalanceSource} — {@code get_previous_balance_source}: {@code
 *       store.checkpoint_states[previous_epoch_observed_justified_checkpoint]}. Used only by
 *       reconfirmation ({@code is_confirmed_chain_safe}), which runs only at epoch-start slots, so
 *       it is {@link Optional} and left empty on other slots.
 *   <li>{@code currentBalanceSource} — {@code get_current_balance_source}: {@code
 *       store.checkpoint_states[current_epoch_observed_justified_checkpoint]}, used for
 *       current-epoch weights.
 *   <li>{@code headBlockState} — {@code store.block_states[get_head(store).root]}, the raw head
 *       state used as the committee shuffling source in {@code get_slot_committee}.
 * </ul>
 *
 * <p>The fourth source, {@code get_pulled_up_head_state}, is derived from {@code headBlockState}
 * (by advancing it to the current epoch if it lags), so it is computed downstream rather than
 * loaded.
 */
record FastConfirmationStates(
    Optional<BeaconState> previousBalanceSource,
    BeaconState currentBalanceSource,
    BeaconState headBlockState) {}
