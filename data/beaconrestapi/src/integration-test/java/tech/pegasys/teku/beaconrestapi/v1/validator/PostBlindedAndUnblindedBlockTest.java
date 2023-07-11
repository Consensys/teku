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

package tech.pegasys.teku.beaconrestapi.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import okhttp3.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlindedBlock;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

public class PostBlindedAndUnblindedBlockTest extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;

  public static Stream<Arguments> postBlockCases() {
    return Stream.of(
        Arguments.of(PostBlock.ROUTE, false, false, false),
        Arguments.of(PostBlock.ROUTE, false, true, false),
        Arguments.of(PostBlindedBlock.ROUTE, true, false, false),
        Arguments.of(PostBlindedBlock.ROUTE, true, true, false),
        // Methods using Eth-Consensus-Version header (only for SSZ)
        Arguments.of(PostBlock.ROUTE, false, true, true),
        Arguments.of(PostBlindedBlock.ROUTE, true, true, true));
  }

  @ParameterizedTest(name = "blinded:{1}_ssz:{2}_version:{3}")
  @MethodSource("postBlockCases")
  void shouldReturnOk(
      final String route,
      final boolean isBlindedBlock,
      final boolean useSsz,
      final boolean useVersionHeader)
      throws IOException {
    startRestAPIAtGenesis(SpecMilestone.BELLATRIX);
    dataStructureUtil = new DataStructureUtil(spec);

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    final SignedBeaconBlockSchema signedBeaconBlockSchema;
    final SignedBeaconBlock request;

    if (isBlindedBlock) {
      request = dataStructureUtil.randomSignedBlindedBeaconBlock(UInt64.ONE);
      signedBeaconBlockSchema =
          spec.atSlot(UInt64.ONE).getSchemaDefinitions().getSignedBlindedBeaconBlockSchema();
    } else {
      request = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
      signedBeaconBlockSchema =
          spec.atSlot(UInt64.ONE).getSchemaDefinitions().getSignedBeaconBlockSchema();
    }

    when(validatorApiChannel.sendSignedBlock(request))
        .thenReturn(SafeFuture.completedFuture(SendSignedBlockResult.success(request.getRoot())));

    Optional<String> milestone = Optional.empty();
    if (useSsz) {
      if (useVersionHeader) {
        milestone = Optional.of("bellatrix");
      }
      try (final Response response =
          postSsz(
              route, signedBeaconBlockSchema.sszSerialize(request).toArrayUnsafe(), milestone)) {
        assertThat(response.code()).isEqualTo(SC_OK);
      }
    } else {
      try (final Response response =
          post(
              route,
              JsonUtil.serialize(request, signedBeaconBlockSchema.getJsonTypeDefinition()))) {
        assertThat(response.code()).isEqualTo(SC_OK);
      }
    }
  }

  @ParameterizedTest(name = "blinded:{1}_ssz:{2}_version:{3}")
  @MethodSource("postBlockCases")
  void shouldReturnOkPostDeneb(
      final String route,
      final boolean isBlindedBlock,
      final boolean useSsz,
      final boolean useVersionHeader)
      throws IOException {
    startRestAPIAtGenesis(SpecMilestone.DENEB);
    dataStructureUtil = new DataStructureUtil(spec);

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    final SignedBlockContainerSchema<SignedBlockContainer> signedBlockContainerSchema;
    final SignedBlockContainer request;

    if (isBlindedBlock) {
      request = dataStructureUtil.randomSignedBlindedBlockContents(UInt64.ONE);
      signedBlockContainerSchema =
          spec.atSlot(UInt64.ONE).getSchemaDefinitions().getSignedBlindedBlockContainerSchema();
    } else {
      request = dataStructureUtil.randomSignedBlockContents(UInt64.ONE);
      signedBlockContainerSchema =
          spec.atSlot(UInt64.ONE).getSchemaDefinitions().getSignedBlockContainerSchema();
    }

    when(validatorApiChannel.sendSignedBlock(request))
        .thenReturn(SafeFuture.completedFuture(SendSignedBlockResult.success(request.getRoot())));

    Optional<String> milestone = Optional.empty();
    if (useSsz) {
      if (useVersionHeader) {
        milestone = Optional.of("deneb");
      }

      try (final Response response =
          postSsz(
              route, signedBlockContainerSchema.sszSerialize(request).toArrayUnsafe(), milestone)) {
        assertThat(response.code()).isEqualTo(SC_OK);
      }
    } else {
      try (final Response response =
          post(
              route,
              JsonUtil.serialize(request, signedBlockContainerSchema.getJsonTypeDefinition()))) {
        assertThat(response.code()).isEqualTo(SC_OK);
      }
    }
  }
}
