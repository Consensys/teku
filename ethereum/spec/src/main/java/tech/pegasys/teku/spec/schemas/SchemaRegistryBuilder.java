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
import tech.pegasys.teku.spec.schemas.providers.AggregateAndProofSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.AttestationSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.AttesterShlashingSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.AttnetsENRFieldSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.BeaconBlockBodySchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.BeaconBlockSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.BeaconBlocksByRootRequestMessageSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.BeaconStateSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.BlindedBeaconBlockBodySchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.BlindedBeaconBlockSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.BlobKzgCommitmentsSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.BlsToExecutionChangeSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.ExecutionPayloadHeaderSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.ExecutionPayloadSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.HistoricalBatchSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.IndexedAttestationSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.MetadataMessageSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.SignedAggregateAndProofSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.SignedBeaconBlockSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.SignedBlindedBeaconBlockSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.SignedBlsToExecutionChangeSchemaProvider;
import tech.pegasys.teku.spec.schemas.providers.SyncnetsENRFieldSchemaProvider;

public class SchemaRegistryBuilder {
  private final List<SchemaProvider<?>> providers = new ArrayList<>();
  private final SchemaCache cache;

  public static SchemaRegistryBuilder create() {
    return new SchemaRegistryBuilder()
        .addProvider(new AggregateAndProofSchemaProvider())
        .addProvider(new SignedAggregateAndProofSchemaProvider())
        .addProvider(new AttestationSchemaProvider())
        .addProvider(new AttesterShlashingSchemaProvider())
        .addProvider(new AttnetsENRFieldSchemaProvider())
        .addProvider(new BeaconBlockBodySchemaProvider())
        .addProvider(new BeaconBlockSchemaProvider())
        .addProvider(new SignedBeaconBlockSchemaProvider())
        .addProvider(new BeaconBlocksByRootRequestMessageSchemaProvider())
        .addProvider(new BeaconStateSchemaProvider())
        .addProvider(new HistoricalBatchSchemaProvider())
        .addProvider(new IndexedAttestationSchemaProvider())
        .addProvider(new MetadataMessageSchemaProvider())
        .addProvider(new SyncnetsENRFieldSchemaProvider())
        .addProvider(new ExecutionPayloadSchemaProvider())
        .addProvider(new ExecutionPayloadHeaderSchemaProvider())
        .addProvider(new BlindedBeaconBlockBodySchemaProvider())
        .addProvider(new BlindedBeaconBlockSchemaProvider())
        .addProvider(new SignedBlindedBeaconBlockSchemaProvider())
        .addProvider(new SignedBlsToExecutionChangeSchemaProvider())
        .addProvider(new BlsToExecutionChangeSchemaProvider())
        .addProvider(new BlobKzgCommitmentsSchemaProvider());
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
