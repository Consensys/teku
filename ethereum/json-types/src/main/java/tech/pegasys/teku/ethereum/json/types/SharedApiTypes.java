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

package tech.pegasys.teku.ethereum.json.types;

import static tech.pegasys.teku.ethereum.json.types.beacon.BlockHeaderData.BLOCK_HEADER_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.beacon.BlockHeaderData;
import tech.pegasys.teku.infrastructure.json.types.DeserializableObjectTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaDataBuilder;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class SharedApiTypes {

  public static final DeserializableTypeDefinition<BlockAndMetaData>
      GET_BLOCK_HEADER_RESPONSE_TYPE =
          DeserializableTypeDefinition.object(BlockAndMetaData.class, BlockAndMetaDataBuilder.class)
              .name("GetBlockHeaderResponse")
              .initializer(BlockAndMetaDataBuilder::new)
              .finisher(BlockAndMetaDataBuilder::build)
              .withField(
                  "data",
                  BLOCK_HEADER_TYPE,
                  blockAndMetaData ->
                      new BlockHeaderData(
                          blockAndMetaData.getData().getRoot(),
                          blockAndMetaData.isCanonical(),
                          blockAndMetaData.getData().asHeader()),
                  (builder, blockHeaderData) ->
                      builder.canonical(blockHeaderData.isCanonical())) // todo fix setter
              .withField(
                  EXECUTION_OPTIMISTIC,
                  BOOLEAN_TYPE,
                  ObjectAndMetaData::isExecutionOptimistic,
                  BlockAndMetaDataBuilder::executionOptimistic)
              .withField(
                  FINALIZED,
                  BOOLEAN_TYPE,
                  ObjectAndMetaData::isFinalized,
                  BlockAndMetaDataBuilder::finalized)
              .build();

  public static final StringValueTypeDefinition<BLSPublicKey> PUBKEY_API_TYPE =
      DeserializableTypeDefinition.string(BLSPublicKey.class)
          .name("Pubkey")
          .formatter(BLSPublicKey::toString)
          .parser(value -> BLSPublicKey.fromBytesCompressedValidate(Bytes48.fromHexString(value)))
          .pattern("^0x[a-fA-F0-9]{96}$")
          .example(
              "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1af526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a")
          .description(
              "The validator's BLS public key, uniquely identifying them. _48-bytes, hex encoded with 0x prefix, case insensitive._")
          .build();

  public static <T extends SszData, S extends SszSchema<T>>
      DeserializableTypeDefinition<T> withDataWrapper(final S schema) {
    return withDataWrapper(schema.getName(), schema.getJsonTypeDefinition());
  }

  public static <T> DeserializableTypeDefinition<T> withDataWrapper(
      final String name, final DeserializableTypeDefinition<T> dataContentType) {
    return withDataWrapper(Optional.of(name), dataContentType);
  }

  private static <T> DeserializableTypeDefinition<T> withDataWrapper(
      final Optional<String> name, final DeserializableTypeDefinition<T> dataContentType) {
    final DeserializableObjectTypeDefinitionBuilder<T, ResponseDataWrapper<T>> builder =
        DeserializableTypeDefinition.object();
    name.ifPresent(builder::name);
    return builder
        .withField("data", dataContentType, Function.identity(), ResponseDataWrapper::setData)
        .initializer(ResponseDataWrapper::new)
        .finisher(ResponseDataWrapper::getData)
        .build();
  }

  private static class ResponseDataWrapper<T> {
    private T data;

    public T getData() {
      return data;
    }

    public void setData(final T data) {
      this.data = data;
    }
  }
}
