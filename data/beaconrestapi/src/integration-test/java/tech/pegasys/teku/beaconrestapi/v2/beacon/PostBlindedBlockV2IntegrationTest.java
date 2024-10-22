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

package tech.pegasys.teku.beaconrestapi.v2.beacon;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.PostBlindedBlockV2;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

@TestSpecContext(allMilestones = true)
public class PostBlindedBlockV2IntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;
  private SpecMilestone specMilestone;
  private DeserializableTypeDefinition<SignedBlockContainer> signedBlockContainerTypeDef;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    startRestAPIAtGenesis(specMilestone);
    dataStructureUtil = specContext.getDataStructureUtil();
    signedBlockContainerTypeDef =
        spec.getGenesisSchemaDefinitions()
            .getSignedBlindedBlockContainerSchema()
            .getJsonTypeDefinition();
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
  }

  @TestTemplate
  void shouldPostBlindedBlock_NoErrors() throws Exception {
    final SignedBeaconBlock blindedBeaconBlock = dataStructureUtil.randomSignedBlindedBeaconBlock();
    when(dataProvider
            .getValidatorDataProvider()
            .submitSignedBlock((SignedBeaconBlock) any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                SendSignedBlockResult.success(blindedBeaconBlock.getRoot())));

    final Response response = post(blindedBeaconBlock, Optional.of(specMilestone));

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @TestTemplate
  void shouldFallbackToSlotSelectorWhenConsensusHeaderIsMissing() throws Exception {
    final SignedBeaconBlock blindedBeaconBlock = dataStructureUtil.randomSignedBlindedBeaconBlock();
    when(dataProvider
            .getValidatorDataProvider()
            .submitSignedBlock((SignedBeaconBlock) any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                SendSignedBlockResult.success(blindedBeaconBlock.getRoot())));

    final Response response = post(blindedBeaconBlock, Optional.empty());

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @TestTemplate
  void shouldFailWhenNodeIsSyncing() throws Exception {
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    final SignedBeaconBlock beaconBlock = dataStructureUtil.randomSignedBlindedBeaconBlock();
    when(dataProvider
            .getValidatorDataProvider()
            .submitSignedBlock((SignedBeaconBlock) any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(SendSignedBlockResult.success(beaconBlock.getRoot())));

    final Response response = post(beaconBlock, Optional.empty());

    assertThat(response.code()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(response.body().string()).contains(RestApiConstants.SERVICE_UNAVAILABLE);
  }

  private Response post(
      final SignedBlockContainer blockContainer, final Optional<SpecMilestone> specMilestone)
      throws IOException {
    return post(
        PostBlindedBlockV2.ROUTE,
        JsonUtil.serialize(blockContainer, signedBlockContainerTypeDef),
        Collections.emptyMap(),
        specMilestone.map(milestone -> milestone.name().toLowerCase(Locale.ROOT)));
  }
}
