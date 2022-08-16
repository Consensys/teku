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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;

public interface ForkChoiceUpdatedResultSubscriber {
  void onForkChoiceUpdatedResult(
      final ForkChoiceUpdatedResultNotification forkChoiceUpdatedResultNotification);

  class ForkChoiceUpdatedResultNotification {
    private final ForkChoiceState forkChoiceState;
    private final boolean isTerminalBlockCall;
    private final SafeFuture<Optional<ForkChoiceUpdatedResult>> forkChoiceUpdatedResult;

    public ForkChoiceUpdatedResultNotification(
        final ForkChoiceState forkChoiceState,
        final boolean isTerminalBlockCall,
        final SafeFuture<Optional<ForkChoiceUpdatedResult>> forkChoiceUpdatedResult) {
      this.forkChoiceState = forkChoiceState;
      this.isTerminalBlockCall = isTerminalBlockCall;
      this.forkChoiceUpdatedResult = forkChoiceUpdatedResult;
    }

    public ForkChoiceState getForkChoiceState() {
      return forkChoiceState;
    }

    public boolean isTerminalBlockCall() {
      return isTerminalBlockCall;
    }

    public SafeFuture<Optional<ForkChoiceUpdatedResult>> getForkChoiceUpdatedResult() {
      return forkChoiceUpdatedResult;
    }
  }
}
