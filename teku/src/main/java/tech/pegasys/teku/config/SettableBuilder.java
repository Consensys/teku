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

package tech.pegasys.teku.config;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Consumer;
import java.util.function.Function;

class SettableBuilder<TBuilder, T> {
  private final Function<TBuilder, T> build;
  private TBuilder builder;
  private boolean hasBeenModified = false;

  SettableBuilder(final TBuilder builder, Function<TBuilder, T> build) {
    this.builder = builder;
    this.build = build;
  }

  public void configure(final Consumer<TBuilder> consumer) {
    hasBeenModified = true;
    consumer.accept(builder);
  }

  public void reset(final TBuilder builder) {
    checkNotNull(builder);
    if (hasBeenModified) {
      throw new IllegalStateException(
          "Builder has already been modified.  Cannot reset the builder.");
    }
    this.builder = builder;
  }

  public T build() {
    return build.apply(builder);
  }
}
