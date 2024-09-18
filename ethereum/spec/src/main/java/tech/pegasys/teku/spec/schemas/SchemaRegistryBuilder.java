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

package tech.pegasys.teku.spec.schemas;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;

public class SchemaRegistryBuilder {
  private final List<SchemaProvider<?>> providers = new ArrayList<>();
  private final SchemaCache cache;

  public static SchemaRegistryBuilder create() {
    return new SchemaRegistryBuilder();
  }

  public SchemaRegistryBuilder() {
    this.cache = SchemaCache.createDefault();
  }

  @VisibleForTesting
  SchemaRegistryBuilder(final SchemaCache cache) {
    this.cache = cache;
  }

  public <T> SchemaRegistryBuilder addProvider(final SchemaProvider<T> provider) {
    providers.add(provider);
    return this;
  }

  public SchemaRegistry build(final SpecMilestone milestone, final SpecConfig specConfig) {
    final SchemaRegistry registry = new SchemaRegistry(milestone, specConfig, cache);

    for (final SchemaProvider<?> provider : providers) {
      if (provider.getSupportedMilestones().contains(milestone)) {
        registry.registerProvider(provider);
      }
    }

    registry.primeRegistry();

    return registry;
  }
}
