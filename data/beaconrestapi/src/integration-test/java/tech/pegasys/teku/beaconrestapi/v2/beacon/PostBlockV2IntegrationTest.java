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
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.PostBlockV2;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

@TestSpecContext(allMilestones = true)
public class PostBlockV2IntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

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
        spec.getGenesisSchemaDefinitions().getSignedBlockContainerSchema().getJsonTypeDefinition();
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
  }

  @TestTemplate
  void shouldPostBlock_NoErrors_preDeneb() throws Exception {
    assumeThat(specMilestone).isLessThan(DENEB);
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock();
    when(dataProvider
            .getValidatorDataProvider()
            .submitSignedBlock((SignedBeaconBlock) any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(SendSignedBlockResult.success(signedBeaconBlock.getRoot())));

    final Response response = post(signedBeaconBlock, Optional.of(specMilestone));

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @TestTemplate
  void shouldFallbackToSlotSelectorWhenConsensusHeaderIsMissing_PreDeneb() throws Exception {
    assumeThat(specMilestone).isLessThan(DENEB);
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock();
    when(dataProvider
            .getValidatorDataProvider()
            .submitSignedBlock((SignedBeaconBlock) any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(SendSignedBlockResult.success(signedBeaconBlock.getRoot())));

    final Response response = post(signedBeaconBlock, Optional.empty());

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @TestTemplate
  void shouldPostBlock_NoErrors_postDeneb() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);
    final SignedBlockContents signedBlockContents = dataStructureUtil.randomSignedBlockContents();
    when(dataProvider
            .getValidatorDataProvider()
            .submitSignedBlock((SignedBlockContents) any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                SendSignedBlockResult.success(signedBlockContents.getRoot())));

    final Response response = post(signedBlockContents, Optional.of(specMilestone));

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @TestTemplate
  void shouldFallbackToSlotSelectorWhenConsensusHeaderIsMissing_postDeneb() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);
    final SignedBlockContents signedBlockContents = dataStructureUtil.randomSignedBlockContents();
    when(dataProvider
            .getValidatorDataProvider()
            .submitSignedBlock((SignedBlockContents) any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                SendSignedBlockResult.success(signedBlockContents.getRoot())));

    final Response response = post(signedBlockContents, Optional.empty());

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEmpty();
  }

  @TestTemplate
  void shouldFailWhenNodeIsSyncing_PreDeneb() throws Exception {
    assumeThat(specMilestone).isLessThan(DENEB);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock();
    when(dataProvider
            .getValidatorDataProvider()
            .submitSignedBlock((SignedBeaconBlock) any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(SendSignedBlockResult.success(signedBeaconBlock.getRoot())));

    final Response response = post(signedBeaconBlock, Optional.empty());

    assertThat(response.code()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(response.body().string()).contains(RestApiConstants.SERVICE_UNAVAILABLE);
  }

  @TestTemplate
  void shouldFailWhenNodeIsSyncing_PostDeneb() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    final SignedBlockContents signedBlockContents = dataStructureUtil.randomSignedBlockContents();
    when(dataProvider
            .getValidatorDataProvider()
            .submitSignedBlock((SignedBeaconBlock) any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                SendSignedBlockResult.success(signedBlockContents.getRoot())));

    final Response response = post(signedBlockContents, Optional.empty());

    assertThat(response.code()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(response.body().string()).contains(RestApiConstants.SERVICE_UNAVAILABLE);
  }

  private Response post(
      final SignedBlockContainer blockContainer, final Optional<SpecMilestone> specMilestone)
      throws IOException {
    return post(
        PostBlockV2.ROUTE,
        JsonUtil.serialize(blockContainer, signedBlockContainerTypeDef),
        Collections.emptyMap(),
        specMilestone.map(milestone -> milestone.name().toLowerCase(Locale.ROOT)));
  }
}
