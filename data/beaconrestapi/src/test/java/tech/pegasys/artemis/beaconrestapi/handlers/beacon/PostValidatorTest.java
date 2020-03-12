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

package tech.pegasys.artemis.beaconrestapi.handlers.beacon;

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.BLSPubKey;
import tech.pegasys.artemis.api.schema.ValidatorsRequest;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

@ExtendWith(MockitoExtension.class)
public class PostValidatorTest {
  @Mock private Context context;
  @Mock private ChainDataProvider provider;
  private final JsonProvider jsonProvider = new JsonProvider();
  private PostValidators handler;

  private final ValidatorsRequest smallRequest =
      new ValidatorsRequest(UnsignedLong.ZERO, List.of(BLSPubKey.empty()));

  @BeforeEach
  public void setup() {
    handler = new PostValidators(provider, jsonProvider);
  }

  @Test
  void shouldReturnNoContentIfNoStore() throws Exception {
    when(provider.getValidatorsByValidatorsRequest(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(context.body()).thenReturn(jsonProvider.objectToJSON(smallRequest));
    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }
}
