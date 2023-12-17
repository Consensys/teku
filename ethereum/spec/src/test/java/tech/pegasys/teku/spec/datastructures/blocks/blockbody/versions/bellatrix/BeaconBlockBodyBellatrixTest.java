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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyTest;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;

class BeaconBlockBodyBellatrixTest extends AbstractBeaconBlockBodyTest<BeaconBlockBodyBellatrix> {

  protected SyncAggregate syncAggregate;
  protected ExecutionPayload executionPayload;
  protected ExecutionPayloadHeader executionPayloadHeader;

  @BeforeEach
  void setup() {
    super.setUpBaseClass(
        SpecMilestone.BELLATRIX,
        () -> {
          syncAggregate = dataStructureUtil.randomSyncAggregate();
          executionPayload = dataStructureUtil.randomExecutionPayload();
          executionPayloadHeader = dataStructureUtil.randomExecutionPayloadHeader();
        });
  }

  @Test
  void equalsReturnsFalseWhenExecutionPayloadIsDifferent() {
    executionPayload = dataStructureUtil.randomExecutionPayload();
    BeaconBlockBodyBellatrix testBeaconBlockBody = safeJoin(createBlockBody());

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenExecutionPayloadHeaderIsDifferent() {
    executionPayloadHeader = dataStructureUtil.randomExecutionPayloadHeader();
    BlindedBeaconBlockBodyBellatrix testBeaconBlockBody = safeJoin(createBlindedBlockBody());

    assertNotEquals(defaultBlindedBlockBody, testBeaconBlockBody);
  }

  @Test
  public void builderShouldFailIfNotPassingPayload() {
    final Exception exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                createBlockBody(
                    createContentProvider(true)
                        .andThen(b -> b.executionPayload(null).executionPayloadHeader(null))));
    assertEquals(
        exception.getMessage(),
        "only and only one of executionPayload or executionPayloadHeader must be set");
  }

  @Test
  public void builderShouldFailIfNotPassingPayloadAndPayloadHeader() {
    final Exception exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                createBlockBody(
                    createContentProvider(true)
                        .andThen(
                            b ->
                                b.executionPayload(SafeFuture.completedFuture(executionPayload)))));
    assertEquals(
        exception.getMessage(),
        "only and only one of executionPayload or executionPayloadHeader must be set");
  }

  @Test
  @SuppressWarnings("unchecked")
  void builderShouldFailWhenOverridingBlindedSchemaWithANullSchema() {
    BeaconBlockBodyBuilderBellatrix beaconBlockBodyBuilderBellatrix =
        new BeaconBlockBodyBuilderBellatrix(null, blindedBlockBodySchema);
    Exception exception =
        assertThrows(
            NullPointerException.class,
            () ->
                beaconBlockBodyBuilderBellatrix
                    .randaoReveal(mock(BLSSignature.class))
                    .eth1Data(mock(Eth1Data.class))
                    .graffiti(mock(Bytes32.class))
                    .attestations(mock(SszList.class))
                    .proposerSlashings(mock(SszList.class))
                    .attesterSlashings(mock(SszList.class))
                    .deposits(mock(SszList.class))
                    .voluntaryExits(mock(SszList.class))
                    .executionPayload(mock(SafeFuture.class))
                    .syncAggregate(mock(SyncAggregate.class))
                    .build());
    assertEquals(exception.getMessage(), "Schema must be specified");
  }

  @Test
  @SuppressWarnings("unchecked")
  void builderShouldFailWhenOverridingSchemaWithANullBlindedSchema() {
    BeaconBlockBodyBuilderBellatrix beaconBlockBodyBuilderBellatrix =
        new BeaconBlockBodyBuilderBellatrix(
            (BeaconBlockBodySchema<? extends BeaconBlockBodyBellatrix>) blockBodySchema, null);
    Exception exception =
        assertThrows(
            NullPointerException.class,
            () ->
                beaconBlockBodyBuilderBellatrix
                    .randaoReveal(mock(BLSSignature.class))
                    .eth1Data(mock(Eth1Data.class))
                    .graffiti(mock(Bytes32.class))
                    .attestations(mock(SszList.class))
                    .proposerSlashings(mock(SszList.class))
                    .attesterSlashings(mock(SszList.class))
                    .deposits(mock(SszList.class))
                    .voluntaryExits(mock(SszList.class))
                    .executionPayloadHeader(mock(SafeFuture.class))
                    .syncAggregate(mock(SyncAggregate.class))
                    .build());
    assertEquals(exception.getMessage(), "Blinded schema must be specified");
  }

  @Override
  protected SafeFuture<BeaconBlockBodyBellatrix> createBlockBody(
      final Consumer<BeaconBlockBodyBuilder> contentProvider) {
    final BeaconBlockBodyBuilder bodyBuilder = createBeaconBlockBodyBuilder();
    contentProvider.accept(bodyBuilder);
    return bodyBuilder.build().thenApply(body -> body.toVersionBellatrix().orElseThrow());
  }

  @Override
  protected SafeFuture<BlindedBeaconBlockBodyBellatrix> createBlindedBlockBody(
      Consumer<BeaconBlockBodyBuilder> contentProvider) {
    final BeaconBlockBodyBuilder bodyBuilder = createBeaconBlockBodyBuilder();
    contentProvider.accept(bodyBuilder);
    return bodyBuilder.build().thenApply(body -> body.toBlindedVersionBellatrix().orElseThrow());
  }

  @Override
  protected Consumer<BeaconBlockBodyBuilder> createContentProvider(final boolean blinded) {
    return super.createContentProvider(blinded)
        .andThen(
            builder -> {
              builder.syncAggregate(syncAggregate);
              if (blinded) {
                builder.executionPayloadHeader(SafeFuture.completedFuture(executionPayloadHeader));
              } else {
                builder.executionPayload(SafeFuture.completedFuture(executionPayload));
              }
            });
  }
}
