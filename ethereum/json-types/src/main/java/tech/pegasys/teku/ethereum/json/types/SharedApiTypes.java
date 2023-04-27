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

package tech.pegasys.teku.ethereum.json.types;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES4_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.wrappers.GetGenesisApiData;
import tech.pegasys.teku.ethereum.json.types.wrappers.GetGenesisApiData.GetGenesisApiDataBuilder;
import tech.pegasys.teku.infrastructure.json.types.DeserializableObjectTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;

public class SharedApiTypes {

  public static final DeserializableTypeDefinition<GetGenesisApiData> GET_GENESIS_API_DATA_TYPE =
      withDataWrapper(
          "GetGenesisResponse",
          DeserializableTypeDefinition.object(
                  GetGenesisApiData.class, GetGenesisApiDataBuilder.class)
              .initializer(GetGenesisApiDataBuilder::new)
              .finisher(GetGenesisApiDataBuilder::build)
              .withField(
                  "genesis_time",
                  UINT64_TYPE,
                  GetGenesisApiData::getGenesisTime,
                  GetGenesisApiDataBuilder::genesisTime)
              .withField(
                  "genesis_validators_root",
                  BYTES32_TYPE,
                  GetGenesisApiData::getGenesisValidatorsRoot,
                  GetGenesisApiDataBuilder::genesisValidatorsRoot)
              .withField(
                  "genesis_fork_version",
                  BYTES4_TYPE,
                  GetGenesisApiData::getGenesisForkVersion,
                  GetGenesisApiDataBuilder::genesisForkVersion)
              .build());

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
