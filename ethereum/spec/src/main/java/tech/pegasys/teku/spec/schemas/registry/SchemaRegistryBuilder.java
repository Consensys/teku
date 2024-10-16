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

import static tech.pegasys.teku.spec.schemas.registry.BaseSchemaProvider.constantProviderBuilder;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTESTATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTNETS_ENR_FIELD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.HISTORICAL_BATCH_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SYNCNETS_ENR_FIELD_SCHEMA;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashSet;
import java.util.Set;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectraSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.phase0.AttestationPhase0Schema;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

public class SchemaRegistryBuilder {
  private final Set<SchemaProvider<?>> providers = new HashSet<>();
  private final Set<SchemaId<?>> schemaIds = new HashSet<>();
  private final SchemaCache cache;

  public static SchemaRegistryBuilder create() {
    return new SchemaRegistryBuilder()
        // PHASE0
        .addProvider(createAttnetsENRFieldSchemaProvider())
        .addProvider(createSyncnetsENRFieldSchemaProvider())
        .addProvider(createBeaconBlocksByRootRequestMessageSchemaProvider())
        .addProvider(createHistoricalBatchSchemaProvider())
        .addProvider(createAttestationSchemaProvider());
  }

  private static SchemaProvider<?> createAttnetsENRFieldSchemaProvider() {
    return constantProviderBuilder(ATTNETS_ENR_FIELD_SCHEMA)
        .withCreator(
            SpecMilestone.PHASE0,
            (registry, specConfig) ->
                SszBitvectorSchema.create(specConfig.getAttestationSubnetCount()))
        .build();
  }

  private static SchemaProvider<?> createSyncnetsENRFieldSchemaProvider() {
    return constantProviderBuilder(SYNCNETS_ENR_FIELD_SCHEMA)
        .withCreator(
            SpecMilestone.PHASE0,
            (registry, specConfig) ->
                SszBitvectorSchema.create(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT))
        .build();
  }

  private static SchemaProvider<?> createBeaconBlocksByRootRequestMessageSchemaProvider() {
    return constantProviderBuilder(BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA)
        .withCreator(
            SpecMilestone.PHASE0,
            (registry, specConfig) -> new BeaconBlocksByRootRequestMessageSchema(specConfig))
        .build();
  }

  private static SchemaProvider<?> createHistoricalBatchSchemaProvider() {
    return constantProviderBuilder(HISTORICAL_BATCH_SCHEMA)
        .withCreator(
            SpecMilestone.PHASE0,
            (registry, specConfig) ->
                new HistoricalBatchSchema(specConfig.getSlotsPerHistoricalRoot()))
        .build();
  }

  private static SchemaProvider<AttestationSchema<Attestation>> createAttestationSchemaProvider() {
    return constantProviderBuilder(ATTESTATION_SCHEMA)
        .withCreator(
            SpecMilestone.PHASE0,
            (registry, specConfig) ->
                new AttestationPhase0Schema(specConfig.getMaxValidatorsPerCommittee())
                    .castTypeToAttestationSchema())
        .withCreator(
            SpecMilestone.DENEB,
            (registry, specConfig) ->
                new AttestationElectraSchema(
                        (long) specConfig.getMaxValidatorsPerCommittee()
                            * specConfig.getMaxCommitteesPerSlot(),
                        specConfig.getMaxCommitteesPerSlot())
                    .castTypeToAttestationSchema())
        .build();
  }

  public SchemaRegistryBuilder() {
    this.cache = SchemaCache.createDefault();
  }

  @VisibleForTesting
  SchemaRegistryBuilder(final SchemaCache cache) {
    this.cache = cache;
  }

  <T> SchemaRegistryBuilder addProvider(final SchemaProvider<T> provider) {
    if (!providers.add(provider)) {
      throw new IllegalArgumentException(
          "The provider " + provider.getClass().getSimpleName() + " has been already added");
    }
    if (!schemaIds.add(provider.getSchemaId())) {
      throw new IllegalStateException(
          "A previously added provider was already providing the schema for "
              + provider.getSchemaId());
    }
    return this;
  }

  public synchronized SchemaRegistry build(
      final SpecMilestone milestone, final SpecConfig specConfig) {
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
