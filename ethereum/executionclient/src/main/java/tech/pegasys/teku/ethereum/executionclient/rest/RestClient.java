/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionclient.rest;

import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;

public interface RestClient {

  SafeFuture<Response<Void>> getAsync(final String apiPath);

  <T> SafeFuture<Response<T>> getAsync(
      final String apiPath, final DeserializableTypeDefinition<T> responseTypeDefinition);

  <S> SafeFuture<Response<Void>> postAsync(
      final String apiPath,
      final S requestBodyObject,
      final SerializableTypeDefinition<S> requestTypeDefinition);

  <T, S> SafeFuture<Response<T>> postAsync(
      final String apiPath,
      final S requestBodyObject,
      final SerializableTypeDefinition<S> requestTypeDefinition,
      final DeserializableTypeDefinition<T> responseTypeDefinition);
}
