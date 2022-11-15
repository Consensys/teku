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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_GENESIS;

import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.ethereum.json.types.GetGenesisData;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class GetGenesisRequest extends AbstractTypeDefRequest {

  public GetGenesisRequest(final OkHttpClient okHttpClient, final HttpUrl baseEndpoint) {
    super(baseEndpoint, okHttpClient);
  }

  public Optional<GetGenesisData> getGenesisData() {
    return get(GET_GENESIS, new ResponseHandler<>(GetGenesisData.GET_GENESIS_DATA_TYPE));
  }
}
