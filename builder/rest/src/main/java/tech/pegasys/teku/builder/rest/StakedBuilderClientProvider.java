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

package tech.pegasys.teku.builder.rest;

import java.util.HashMap;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.spec.Spec;

public class StakedBuilderClientProvider {

  private final Spec spec;
  private final AsyncRunner asyncRunner;

  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
  private final Map<String, StakedBuilderClient> clients = new HashMap<>();

  public StakedBuilderClientProvider(final Spec spec, final AsyncRunner asyncRunner) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
  }

  public StakedBuilderClient getClient(final String url) {
    return clients.computeIfAbsent(
        url,
        __ -> new OkHttpStakedBuilderClient(asyncRunner, spec, HttpUrl.get(url), okHttpClient));
  }
}
