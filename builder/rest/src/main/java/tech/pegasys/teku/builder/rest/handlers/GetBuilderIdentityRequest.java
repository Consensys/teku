/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.builder.rest.handlers;

import static tech.pegasys.teku.builder.rest.BuilderApiMethod.GET_BUILDER_IDENTITY;

import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.builder.rest.BuilderIdentity;
import tech.pegasys.teku.builder.rest.ResponseHandler;

public class GetBuilderIdentityRequest extends AbstractBuilderRequest {

  public GetBuilderIdentityRequest(final HttpUrl baseEndpoint, final OkHttpClient httpClient) {
    super(baseEndpoint, httpClient);
  }

  public Optional<BuilderIdentity> submit() {
    return get(GET_BUILDER_IDENTITY, new ResponseHandler<>(BuilderIdentity.TYPE));
  }
}
