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

package tech.pegasys.teku.spec.config.builder;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import tech.pegasys.teku.spec.config.SpecConfig;

/** TODO */
@SuppressWarnings("rawtypes")
class BuilderChain implements ForkConfigBuilder<SpecConfig, SpecConfig> {

  private final ForkConfigBuilder builderToApply;
  private final ForkConfigBuilder tail;

  private BuilderChain(final ForkConfigBuilder builderToApply, final ForkConfigBuilder tail) {
    this.builderToApply = builderToApply;
    this.tail = tail;
  }

  public static BuilderChain create(final ForkConfigBuilder<?, ?> builder) {
    return new BuilderChain(builder, new NoOpForkBuilder());
  }

  @SuppressWarnings("unchecked")
  public <T extends ForkConfigBuilder<?, ?>> void withBuilder(
      final Class<T> type, final Consumer<T> consumer) {
    if (type.isInstance(builderToApply)) {
      consumer.accept((T) builderToApply);
    } else if (tail instanceof BuilderChain) {
      ((BuilderChain) tail).withBuilder(type, consumer);
    } else {
      throw new IllegalArgumentException("Unrecognized config type: " + type.getCanonicalName());
    }
  }

  public BuilderChain appendBuilder(final ForkConfigBuilder<?, ?> newBuilder) {
    final BuilderChain newTail;
    if (tail instanceof BuilderChain) {
      newTail = ((BuilderChain) tail).appendBuilder(newBuilder);
    } else {
      newTail = new BuilderChain(newBuilder, new NoOpForkBuilder<>());
    }
    return new BuilderChain(builderToApply, newTail);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    builderToApply.addOverridableItemsToRawConfig(rawConfig);
    tail.addOverridableItemsToRawConfig(rawConfig);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Optional<SpecConfig> build(final SpecConfig specConfig) {
    final Optional<SpecConfig> maybeConfig = builderToApply.build(specConfig);
    if (maybeConfig.isEmpty()) {
      return Optional.of(specConfig);
    } else {
      return tail.build(maybeConfig.get());
    }
  }

  @Override
  public void validate() {
    builderToApply.validate();
    tail.validate();
  }

  private static class NoOpForkBuilder<T extends SpecConfig> implements ForkConfigBuilder<T, T> {

    @Override
    public Optional<T> build(final T specConfig) {
      return Optional.of(specConfig);
    }

    @Override
    public void validate() {}

    @Override
    public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
  }
}
