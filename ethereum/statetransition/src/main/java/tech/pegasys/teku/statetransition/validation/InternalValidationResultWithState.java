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

package tech.pegasys.teku.statetransition.validation;

import com.google.errorprone.annotations.FormatMethod;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class InternalValidationResultWithState {

  private static final InternalValidationResultWithState IGNORE_WITH_NO_STATE =
      new InternalValidationResultWithState(InternalValidationResult.IGNORE, Optional.empty());
  private static final InternalValidationResultWithState SAVE_FOR_FUTURE_WITH_NO_STATE =
      new InternalValidationResultWithState(
          InternalValidationResult.SAVE_FOR_FUTURE, Optional.empty());
  private final InternalValidationResult result;
  private final Optional<BeaconState> state;

  private InternalValidationResultWithState(
      final InternalValidationResult result, final Optional<BeaconState> state) {
    this.result = result;
    this.state = state;
  }

  public static InternalValidationResultWithState accept(final BeaconState state) {
    return new InternalValidationResultWithState(
        InternalValidationResult.ACCEPT, Optional.of(state));
  }

  public static InternalValidationResultWithState saveForFuture() {
    return SAVE_FOR_FUTURE_WITH_NO_STATE;
  }

  public static InternalValidationResultWithState ignore() {
    return IGNORE_WITH_NO_STATE;
  }

  public static InternalValidationResultWithState ignore(final BeaconState state) {
    return new InternalValidationResultWithState(
        InternalValidationResult.IGNORE, Optional.of(state));
  }

  @FormatMethod
  public static InternalValidationResultWithState reject(
      final String descriptionTemplate, final Object... args) {
    return new InternalValidationResultWithState(
        InternalValidationResult.reject(descriptionTemplate, args), Optional.empty());
  }

  @FormatMethod
  public static InternalValidationResultWithState ignore(
      final String descriptionTemplate, final Object... args) {
    return new InternalValidationResultWithState(
        InternalValidationResult.ignore(descriptionTemplate, args), Optional.empty());
  }

  @FormatMethod
  public static InternalValidationResultWithState ignore(
      final BeaconState state, final String descriptionTemplate, final Object... args) {
    return new InternalValidationResultWithState(
        InternalValidationResult.ignore(descriptionTemplate, args), Optional.of(state));
  }

  public InternalValidationResult getResult() {
    return result;
  }

  public Optional<BeaconState> getState() {
    return state;
  }
}
