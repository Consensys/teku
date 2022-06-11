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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;

class PostPrepareBeaconProposerTest {

  private final Context context = mock(Context.class);
  private final ValidatorDataProvider provider = mock(ValidatorDataProvider.class);
  private final JsonProvider jsonProvider = new JsonProvider();

  private final PostPrepareBeaconProposer handler = new PostPrepareBeaconProposer(provider, true);

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() {
    final String input = "{\"foo\": \"bar\"}";
    when(context.bodyAsInputStream()).thenReturn(IOUtils.toInputStream(input, UTF_8));

    assertThatThrownBy(() -> handler.handle(context)).isInstanceOf(JsonProcessingException.class);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnSuccessWhenPostingValidData() throws Exception {
    final BeaconPreparableProposer proposer1 =
        new BeaconPreparableProposer(UInt64.valueOf(1), Eth1Address.ZERO);
    final BeaconPreparableProposer proposer2 =
        new BeaconPreparableProposer(
            UInt64.valueOf(10),
            Eth1Address.fromHexString("0x1aD91ee08f21bE3dE0BA2ba6918E714dA6B45836"));

    final String requestJson = jsonProvider.objectToJSON(List.of(proposer1, proposer2));
    when(context.bodyAsInputStream()).thenReturn(IOUtils.toInputStream(requestJson, UTF_8));

    handler.handle(context);

    verify(context).status(SC_OK);
  }
}
