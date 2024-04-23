/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.validator.api;

import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;

public class UpdatableGraffitiProvider implements GraffitiProvider {
  private final Supplier<Optional<Bytes32>> storageProvider;
  private final GraffitiProvider defaultProvider;

  public UpdatableGraffitiProvider(
      final Supplier<Optional<Bytes32>> storageProvider, final GraffitiProvider defaultProvider) {
    this.storageProvider = storageProvider;
    this.defaultProvider = defaultProvider;
  }

  @Override
  public Optional<Bytes32> get() {
    return getFromStorage().or(defaultProvider::get);
  }

  private Optional<Bytes32> getFromStorage() {
    try {
      return storageProvider.get();
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  /**
   * @return graffiti without checking for thrown Exceptions.
   */
  public Optional<Bytes32> getUnsafe() {
    return storageProvider.get().or(defaultProvider::get);
  }
}
