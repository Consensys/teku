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

package tech.pegasys.teku.beaconrestapi.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.validator.GetAggregate;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetAggregateIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldReturnNotFoundWhenCreateAggregateReturnsEmpty() throws Exception {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final Map<String, String> params =
        Map.of(
            "attestation_data_root", attestation.hash_tree_root().toHexString(),
            "slot", UInt64.ONE.toString());

    when(validatorApiChannel.createAggregate(UInt64.ONE, attestation.hash_tree_root()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    Response response = getResponse(GetAggregate.ROUTE, params);
    assertThat(response.code()).isEqualTo(404);
  }

  @Test
  public void shouldSucceedWhenCreateAggregateReturnsAttestation() throws Exception {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final Map<String, String> params =
        Map.of(
            "attestation_data_root", attestation.hash_tree_root().toHexString(),
            "slot", UInt64.ONE.toString());

    when(validatorApiChannel.createAggregate(UInt64.ONE, attestation.hash_tree_root()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(attestation)));

    Response response = getResponse(GetAggregate.ROUTE, params);
    assertThat(response.code()).isEqualTo(200);
    tech.pegasys.teku.api.schema.Attestation schemaAttestation =
        new tech.pegasys.teku.api.schema.Attestation(attestation);
    assertThat(response.body().string()).isEqualTo(jsonProvider.objectToJSON(schemaAttestation));
  }
}
