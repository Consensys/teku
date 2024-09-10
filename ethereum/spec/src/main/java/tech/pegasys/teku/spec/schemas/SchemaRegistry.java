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
import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.SchemaTypes.SchemaId;

public class SchemaRegistry {
  private final Map<SchemaId<?>, SchemaProvider<?>> providers = new HashMap<>();
  private final SpecMilestone milestone;
  private final SchemaCache cache;
  private final SpecConfig specConfig;

  public SchemaRegistry(
      final SpecMilestone milestone, final SpecConfig specConfig, final SchemaCache cache) {
    this.milestone = milestone;
    this.specConfig = specConfig;
    this.cache = cache;
  }

  public void registerProvider(final SchemaProvider<?> provider) {
    providers.put(provider.getSchemaId(), provider);
  }

  @VisibleForTesting
  boolean isProviderRegistered(final SchemaProvider<?> provider) {
    return provider.equals(providers.get(provider.getSchemaId()));
  }

  @SuppressWarnings("unchecked")
  public <T> T get(final SchemaId<T> schemaClass) {
    SchemaProvider<T> provider = (SchemaProvider<T>) providers.get(schemaClass);
    if (provider == null) {
      throw new IllegalArgumentException(
          "No provider registered for schema "
              + schemaClass
              + " or it does not support milestone "
              + milestone);
    }
    T schema = cache.get(milestone, schemaClass);
    if (schema != null) {
      return schema;
    }
    final SpecMilestone effectiveMilestone = provider.getEffectiveMilestone(milestone);
    if (effectiveMilestone != milestone) {
      schema = cache.get(effectiveMilestone, schemaClass);
      if (schema != null) {
        cache.put(milestone, schemaClass, schema);
        return schema;
      }
    }
    schema = provider.getSchema(this);
    cache.put(effectiveMilestone, schemaClass, schema);
    if (effectiveMilestone != milestone) {
      cache.put(milestone, schemaClass, schema);
    }
    return schema;
  }

  public SpecMilestone getMilestone() {
    return milestone;
  }

  public SpecConfig getSpecConfig() {
    return specConfig;
  }

  public void primeRegistry() {
    for (final SchemaId<?> schemaClass : providers.keySet()) {
      get(schemaClass);
    }
  }
}
