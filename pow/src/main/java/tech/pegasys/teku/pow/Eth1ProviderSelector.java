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

package tech.pegasys.teku.pow;

import com.google.common.base.Preconditions;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class Eth1ProviderSelector {
  private final List<MonitorableEth1Provider> candidates;
  private boolean initialValidationCompleted;

  public Eth1ProviderSelector(final List<MonitorableEth1Provider> candidates) {
    Preconditions.checkArgument(candidates != null && !candidates.isEmpty());
    this.candidates = candidates;
    this.initialValidationCompleted = false;
  }

  public static class ValidEth1ProviderIterator {
    private Iterator<MonitorableEth1Provider> currentIterator;

    private ValidEth1ProviderIterator(Iterator<MonitorableEth1Provider> currentIterator) {
      this.currentIterator = currentIterator;
    }

    public Optional<MonitorableEth1Provider> next() {

      while (currentIterator.hasNext()) {
        final MonitorableEth1Provider current = currentIterator.next();
        if (current.isValid()) {
          return Optional.of(current);
        }
      }

      return Optional.empty();
    }
  }

  public void notifyValidationCompleted() {
    initialValidationCompleted = true;
  }

  public boolean isInitialValidationCompleted() {
    return initialValidationCompleted;
  }

  public List<MonitorableEth1Provider> getProviders() {
    return candidates;
  }

  public ValidEth1ProviderIterator getValidProviderIterator() {
    return new ValidEth1ProviderIterator(candidates.iterator());
  }
}
