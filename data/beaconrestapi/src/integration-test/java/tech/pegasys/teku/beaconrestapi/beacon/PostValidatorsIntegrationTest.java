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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.RestApiConstants;
import tech.pegasys.teku.beaconrestapi.handlers.beacon.PostValidators;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainProperties;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public class PostValidatorsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private static final List<BLSKeyPair> keys = BLSKeyGenerator.generateKeyPairs(1);

  @Test
  public void shouldReturnNoContentIfStoreNotDefined() throws Exception {
    startPreGenesisRestAPI();

    final Response response = post(1, keys);
    assertNoContent(response);
  }

  @Test
  public void shouldReturnNoContentIfPreForkChoice() throws Exception {
    startPreForkChoiceRestAPI();

    final Response response = post(1, keys);
    assertNoContent(response);
  }

  @Test
  public void shouldHandleMissingFinalizedState() throws Exception {
    startRestAPIAtGenesis(StateStorageMode.PRUNE);
    final int outOfRangeSlot = 20;
    final int finalizedSlot = 20 + Constants.SLOTS_PER_HISTORICAL_ROOT;
    createBlocksAtSlots(outOfRangeSlot, finalizedSlot);
    final UnsignedLong finalizedEpoch =
        ChainProperties.computeBestEpochFinalizableAtSlot(finalizedSlot);
    final SignedBlockAndState finalizedBlock = finalizeChainAtEpoch(finalizedEpoch);
    assertThat(finalizedBlock.getSlot()).isEqualTo(UnsignedLong.valueOf(finalizedSlot));

    final int targetEpoch = finalizedEpoch.minus(UnsignedLong.ONE).intValue();
    final Response response = post(targetEpoch, keys);
    assertGone(response);
  }

  @Test
  public void shouldReturnEmptyListIfWhenPubKeysIsEmpty() throws Exception {
    startRestAPIAtGenesis(StateStorageMode.PRUNE);

    final Response response = post(1, Collections.emptyList());
    assertBodyEquals(response, "[]");
  }

  private Response post(final int epoch, final List<BLSKeyPair> publicKeys) throws IOException {
    final List<String> publicKeyStrings =
        publicKeys.stream()
            .map(k -> k.getPublicKey().toBytes().toHexString())
            .collect(Collectors.toList());

    final Map<String, Object> params =
        Map.of(RestApiConstants.EPOCH, epoch, "pubkeys", publicKeyStrings);
    return post(PostValidators.ROUTE, mapToJson(params));
  }
}
