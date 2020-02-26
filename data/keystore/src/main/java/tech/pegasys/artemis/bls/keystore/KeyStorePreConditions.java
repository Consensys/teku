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

package tech.pegasys.artemis.bls.keystore;

import org.checkerframework.checker.nullness.qual.NonNull;

/** Similar to guava Preconditions but throws our custom exception */
public class KeyStorePreConditions {
  public static <T extends @NonNull Object> void checkNotNull(T reference, String errorMessage) {
    if (reference == null) {
      throw new KeyStoreValidationException(errorMessage);
    }
  }

  public static void checkArgument(boolean expression, String errorMessage) {
    if (!expression) {
      throw new KeyStoreValidationException(errorMessage);
    }
  }
}
