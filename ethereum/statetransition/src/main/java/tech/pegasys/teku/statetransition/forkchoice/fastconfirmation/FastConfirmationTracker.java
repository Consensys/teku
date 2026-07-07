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
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;

public class FastConfirmationTracker {

  private final boolean enabled;
  private final AtomicReference<FastConfirmationStore> fastConfirmationStore =
      new AtomicReference<>();

  private FastConfirmationTracker(final boolean enabled) {
    this.enabled = enabled;
  }

  public static FastConfirmationTracker create(final boolean enabled) {
    return new FastConfirmationTracker(enabled);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void initialize(final ReadOnlyStore store) {
    if (!enabled) {
      return;
    }
    fastConfirmationStore.set(FastConfirmationStore.create(store));
  }

  public Optional<FastConfirmationStore> getFastConfirmationStore() {
    return Optional.ofNullable(fastConfirmationStore.get());
  }
}
