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

package tech.pegasys.teku.spec.schemas.registry;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

public class SchemaRegistry {
  // this is used for dependency loop detection during priming
  private static final Set<SchemaProvider<?>> INFLIGHT_PROVIDERS = new HashSet<>();

  private final Map<SchemaId<?>, SchemaProvider<?>> providers = new HashMap<>();
  private final SpecMilestone milestone;
  private final SchemaCache cache;
  private final SpecConfig specConfig;
  private boolean primed;

  SchemaRegistry(
      final SpecMilestone milestone, final SpecConfig specConfig, final SchemaCache cache) {
    this.milestone = milestone;
    this.specConfig = specConfig;
    this.cache = cache;
    this.primed = false;
  }

  /**
   * This is supposed to be called only by {@link SchemaRegistryBuilder#build(SpecMilestone,
   * SpecConfig)} which is synchronized
   */
  void registerProvider(final SchemaProvider<?> provider) {
    if (primed) {
      throw new IllegalStateException("Cannot add a provider to a primed registry");
    }
    if (providers.put(provider.getSchemaId(), provider) != null) {
      throw new IllegalStateException(
          "Cannot add provider "
              + provider.getClass().getSimpleName()
              + " referencing "
              + provider.getSchemaId()
              + " which has been already added via another provider");
    }
  }

  @VisibleForTesting
  boolean isProviderRegistered(final SchemaProvider<?> provider) {
    return provider.equals(providers.get(provider.getSchemaId()));
  }

  @SuppressWarnings("unchecked")
  public <T> T get(final SchemaId<T> schemaId) {
    SchemaProvider<T> provider = (SchemaProvider<T>) providers.get(schemaId);
    if (provider == null) {
      throw new IllegalArgumentException(
          "No provider registered for schema "
              + schemaId
              + " or it does not support milestone "
              + milestone);
    }
    T schema = cache.get(milestone, schemaId);
    if (schema != null) {
      return schema;
    }

    // let's check if the schema is stored associated to the effective milestone
    final SpecMilestone effectiveMilestone = provider.getEffectiveMilestone(milestone);
    if (effectiveMilestone != milestone) {
      schema = cache.get(effectiveMilestone, schemaId);
      if (schema != null) {
        // let's cache the schema for current milestone as well
        cache.put(milestone, schemaId, schema);
        return schema;
      }
    }

    // The schema was not found.
    // we reach this point only during priming when we actually ask providers to generate schemas
    checkState(!primed, "Registry is primed but schema not found for %s", schemaId);

    // save the provider as "inflight"
    if (!INFLIGHT_PROVIDERS.add(provider)) {
      throw new IllegalStateException("loop detected creating schema for " + schemaId);
    }

    // actual schema creation (may trigger recursive registry lookups)
    schema = provider.getSchema(this);

    // release the provider
    INFLIGHT_PROVIDERS.remove(provider);

    // cache the schema
    cache.put(effectiveMilestone, schemaId, schema);
    if (effectiveMilestone != milestone) {
      cache.put(milestone, schemaId, schema);
    }
    return schema;
  }

  public SpecMilestone getMilestone() {
    return milestone;
  }

  public SpecConfig getSpecConfig() {
    return specConfig;
  }

  /**
   * This is supposed to be called only by {@link SchemaRegistryBuilder#build(SpecMilestone,
   * SpecConfig)} which is synchronized
   */
  void primeRegistry() {
    if (primed) {
      throw new IllegalStateException("Registry already primed");
    }
    for (final SchemaId<?> schemaClass : providers.keySet()) {
      get(schemaClass);
    }
    primed = true;
  }
}
