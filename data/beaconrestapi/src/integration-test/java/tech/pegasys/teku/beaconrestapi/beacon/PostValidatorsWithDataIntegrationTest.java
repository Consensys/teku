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

package tech.pegasys.teku.beaconrestapi.beacon;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.ValidatorWithIndex;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.RestApiConstants;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.PostValidators;
import tech.pegasys.teku.bls.BLSKeyPair;

public class PostValidatorsWithDataIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  void shouldRetrieveValidatorsWhenBlockPresentAtEpoch() throws Exception {
    createBlocksAtSlotsAndMapToApiResult(SEVEN, EIGHT);

    final Response response = post(Optional.of(1), VALIDATOR_KEYS);
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  void shouldRetrieveValidatorsWhenEpochNotSpecified() throws Exception {
    createBlocksAtSlotsAndMapToApiResult(SEVEN, EIGHT);

    final Response response = post(Optional.empty(), VALIDATOR_KEYS);
    assertThat(response.code()).isEqualTo(SC_OK);

    final ValidatorWithIndex[] validators =
        jsonProvider.jsonToObject(response.body().string(), ValidatorWithIndex[].class);
    assertThat(validators.length).isEqualTo(VALIDATOR_KEYS.size());
  }

  @Test
  void shouldRetrieveValidatorsWhenBlockMissingAtEpoch() throws Exception {
    createBlocksAtSlotsAndMapToApiResult(SEVEN, NINE);

    final Response response = post(Optional.of(1), VALIDATOR_KEYS);
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  private Response post(final Optional<Integer> epoch, final List<BLSKeyPair> publicKeys)
      throws IOException {
    final List<String> publicKeyStrings =
        publicKeys.stream()
            .map(k -> k.getPublicKey().toBytes().toHexString())
            .collect(Collectors.toList());

    final Map<String, Object> params =
        epoch.isEmpty()
            ? Map.of("pubkeys", publicKeyStrings)
            : Map.of(RestApiConstants.EPOCH, epoch.get(), "pubkeys", publicKeyStrings);
    return post(PostValidators.ROUTE, mapToJson(params));
  }
}
