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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static tech.pegasys.teku.ethereum.json.types.config.SpecConfigDataMapBuilder.GET_SPEC_RESPONSE_TYPE;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_CONFIG_SPEC;

import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class GetSpecRequest extends AbstractTypeDefRequest {

  public GetSpecRequest(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public Optional<Map<String, String>> submit() {
    return get(GET_CONFIG_SPEC, Map.of(), Map.of(), new ResponseHandler<>(GET_SPEC_RESPONSE_TYPE));
  }
}
