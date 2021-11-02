/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.v2.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.SchemaObjectProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v2.validator.GetNewBlockResponseV2;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetNewBlockV2Test {
  private final tech.pegasys.teku.bls.BLSSignature signatureInternal =
      BLSTestUtil.randomSignature(1234);
  private final BLSSignature signature = new BLSSignature(signatureInternal);
  private final Context context = mock(Context.class);
  private final ValidatorDataProvider provider = mock(ValidatorDataProvider.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private GetNewBlock handler;
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @SuppressWarnings("unchecked")
  final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  @BeforeEach
  public void setup() {
    handler = new GetNewBlock(provider, jsonProvider);
  }

  @Test
  void shouldReturnBlockWithoutGraffiti() throws Exception {
    final Map<String, String> pathParams = Map.of(SLOT, "1");
    final SchemaObjectProvider schemaProvider = new SchemaObjectProvider(spec);
    final Map<String, List<String>> queryParams =
        Map.of(RANDAO_REVEAL, List.of(signature.toHexString()));

    final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock randomBeaconBlock =
        dataStructureUtil.randomBeaconBlock(ONE);

    final BeaconBlock altairBlock = schemaProvider.getBeaconBlock(randomBeaconBlock);
    when(context.queryParamMap()).thenReturn(queryParams);
    when(context.pathParamMap()).thenReturn(pathParams);
    when(provider.getMilestoneAtSlot(UInt64.ONE)).thenReturn(SpecMilestone.ALTAIR);
    when(provider.getUnsignedBeaconBlockAtSlot(ONE, signature, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(altairBlock)));
    handler.handle(context);

    verify(context).future(args.capture());
    SafeFuture<String> result = args.getValue();
    assertThat(result)
        .isCompletedWithValue(
            jsonProvider.objectToJSON(
                new GetNewBlockResponseV2(SpecMilestone.ALTAIR, altairBlock)));
  }
}
