/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.statetransition.attestation;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class PayloadAttestationPool {

  private final Spec spec;
  private final SettableGauge sizeGauge;

  private final AtomicInteger size = new AtomicInteger(0);

  public PayloadAttestationPool(final Spec spec, final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.sizeGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "payload_attestation_pool_size",
            "The number of payload attestations available to be included in proposed blocks");
  }

  // EIP7732 TODO: implement
  @SuppressWarnings("unused")
  public synchronized void add(final PayloadAttestationMessage payloadAttestationMessage) {
    updateSize(1);
  }

  private void updateSize(final int delta) {
    final int currentSize = size.addAndGet(delta);
    sizeGauge.set(currentSize);
  }

  // EIP7732 TODO: implement
  @SuppressWarnings("unused")
  public synchronized SszList<PayloadAttestation> getPayloadAttestationsForBlock(
      final BeaconState stateAtBlockSlot) {

    final SchemaDefinitions schemaDefinitions =
        spec.atSlot(stateAtBlockSlot.getSlot()).getSchemaDefinitions();

    final SszListSchema<PayloadAttestation, ?> attestationsSchema =
        schemaDefinitions.getBeaconBlockBodySchema().getPayloadAttestationsSchema();

    return attestationsSchema.createFromElements(List.of());
  }
}
