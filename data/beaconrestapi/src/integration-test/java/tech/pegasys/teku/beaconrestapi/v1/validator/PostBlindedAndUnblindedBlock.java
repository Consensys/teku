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
import java.util.stream.Stream;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlindedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContentsSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

public class PostBlindedAndUnblindedBlock extends AbstractDataBackedRestAPIIntegrationTest {
  private DataStructureUtil dataStructureUtil;

  public static Stream<Arguments> postBlockCases() {
    return Stream.of(
        Arguments.of(PostBlock.ROUTE, false, false),
        Arguments.of(PostBlock.ROUTE, false, true),
        Arguments.of(PostBlindedBlock.ROUTE, true, false),
        Arguments.of(PostBlindedBlock.ROUTE, true, true));
  }

  @BeforeEach
  void setup() {}

  @ParameterizedTest(name = "blinded:{1}_ssz:{2}")
  @MethodSource("postBlockCases")
  void shouldReturnBeaconBlock_Ok_PreDeneb(
      final String route, final boolean isBlindedBlock, final boolean useSsz) throws IOException {

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

    if (useSsz) {
      try (Response response =
          postSsz(route, signedBeaconBlockSchema.sszSerialize(request).toArrayUnsafe())) {
        assertThat(response.code()).isEqualTo(SC_OK);
      }
    } else {
      try (Response response =
          post(
              route,
              JsonUtil.serialize(request, signedBeaconBlockSchema.getJsonTypeDefinition()))) {
        assertThat(response.code()).isEqualTo(SC_OK);
      }
    }
  }

  @ParameterizedTest(name = "blinded:{1}_ssz:{2}")
  @MethodSource("postBlockCases")
  void shouldReturnBlockContents_Ok_PostDeneb(
      final String route, final boolean isBlindedBlock, final boolean useSsz) throws IOException {
    startRestAPIAtGenesis(SpecMilestone.DENEB);
    dataStructureUtil = new DataStructureUtil(spec);

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    if (isBlindedBlock) {
      final SignedBlindedBlockContents request =
          dataStructureUtil.randomSignedBlindedBlockContents(UInt64.ONE);
      final SignedBlindedBlockContentsSchema signedBlindedBlockContentsSchema =
          spec.atSlot(UInt64.ONE)
              .getSchemaDefinitions()
              .toVersionDeneb()
              .orElseThrow()
              .getSignedBlindedBlockContentsSchema();
      when(validatorApiChannel.sendSignedBlindedBlockContents(request))
          .thenReturn(
              SafeFuture.completedFuture(
                  SendSignedBlockResult.success(request.getSignedBeaconBlock().getRoot())));
      if (useSsz) {
        try (Response response =
            postSsz(
                route, signedBlindedBlockContentsSchema.sszSerialize(request).toArrayUnsafe())) {
          assertThat(response.code()).isEqualTo(SC_OK);
        }
      } else {
        try (Response response =
            post(
                route,
                JsonUtil.serialize(
                    request, signedBlindedBlockContentsSchema.getJsonTypeDefinition()))) {
          assertThat(response.code()).isEqualTo(SC_OK);
        }
      }
    } else {
      final SignedBlockContents request = dataStructureUtil.randomSignedBlockContents(UInt64.ONE);
      final SignedBlockContentsSchema signedBlockContentsSchema =
          spec.atSlot(UInt64.ONE)
              .getSchemaDefinitions()
              .toVersionDeneb()
              .orElseThrow()
              .getSignedBlockContentsSchema();
      when(validatorApiChannel.sendSignedBlockContents(request))
          .thenReturn(
              SafeFuture.completedFuture(
                  SendSignedBlockResult.success(request.getSignedBeaconBlock().getRoot())));
      if (useSsz) {
        try (Response response =
            postSsz(route, signedBlockContentsSchema.sszSerialize(request).toArrayUnsafe())) {
          assertThat(response.code()).isEqualTo(SC_OK);
        }
      } else {
        try (Response response =
            post(
                route,
                JsonUtil.serialize(request, signedBlockContentsSchema.getJsonTypeDefinition()))) {
          assertThat(response.code()).isEqualTo(SC_OK);
        }
      }
    }
  }
}
