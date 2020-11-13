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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.provider.JsonProvider;

public class PutLogLevelTest {

  private final JsonProvider jsonProvider = new JsonProvider();

  private Context context = mock(Context.class);
  private PutLogLevel handler;

  @BeforeEach
  public void setup() {
    handler = new PutLogLevel(jsonProvider);
  }

  @Test
  public void shouldReturnBadRequestWhenLevelIsMissing() throws Exception {
    when(context.body()).thenReturn("{\"a\": \"field\"}");
    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenLevelIsInvalid() throws Exception {
    when(context.body()).thenReturn("{\"level\": \"I do not exist\"}");
    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnNoContentWhenLevelIsValid() throws Exception {
    when(context.body()).thenReturn("{\"level\": \"inFO\"}");
    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }

  @Test
  public void shouldReturnBadRequestWhenLFilterIsInvalidJson() throws Exception {
    when(context.body())
        .thenReturn("{\"level\": \"I do not exist\", \"log_filter\": \"a.class.somewhere\"}");
    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnNoContentWhenLevelAndFilterAreValid() throws Exception {
    when(context.body())
        .thenReturn("{\"level\": \"InfO\", \"log_filter\": [\"a.class.somewhere\"]}");
    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }
}
