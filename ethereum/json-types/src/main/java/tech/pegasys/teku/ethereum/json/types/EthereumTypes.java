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
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class EthereumTypes {

  public static final DeserializableTypeDefinition<Eth1Address> ETH1ADDRESS_TYPE =
      DeserializableTypeDefinition.string(Eth1Address.class)
          .formatter(Eth1Address::toHexString)
          .parser(Eth1Address::fromHexString)
          .example("0x1Db3439a222C519ab44bb1144fC28167b4Fa6EE6")
          .description("Hex encoded deposit contract address with 0x prefix")
          .format("byte")
          .build();

  public static final StringValueTypeDefinition<BLSSignature> SIGNATURE_TYPE =
      DeserializableTypeDefinition.string(BLSSignature.class)
          .formatter(BLSSignature::toString)
          .parser(value -> BLSSignature.fromBytesCompressed(Bytes.fromHexString(value)))
          .example(
              "0x1b66ac1fb663c9bc59509846d6ec05345bd908eda73e670af888da41af171505"
                  + "cc411d61252fb6cb3fa0017b679f8bb2305b26a285fa2737f175668d0dff91cc"
                  + "1b66ac1fb663c9bc59509846d6ec05345bd908eda73e670af888da41af171505")
          .description("`BLSSignature Hex` BLS12-381 signature for the current epoch.")
          .format("byte")
          .build();

  public static final StringValueTypeDefinition<BLSPublicKey> PUBLIC_KEY_TYPE =
      DeserializableTypeDefinition.string(BLSPublicKey.class)
          .formatter(BLSPublicKey::toString)
          .parser(BLSPublicKey::fromHexString)
          .example(
              "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1a"
                  + "f526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a")
          .description(
              "`BLSPublicKey Hex` The validator's BLS public key, uniquely identifying them. "
                  + "48-bytes, hex encoded with 0x prefix, case insensitive.")
          .format("string")
          .build();

  public static final SerializableTypeDefinition<Checkpoint> CHECKPOINT_TYPE =
      SerializableTypeDefinition.object(Checkpoint.class)
          .name("Checkpoint")
          .withField("epoch", UINT64_TYPE, Checkpoint::getEpoch)
          .withField("root", BYTES32_TYPE, Checkpoint::getRoot)
          .build();

  public static final DeserializableTypeDefinition<SpecMilestone> MILESTONE_TYPE =
      DeserializableTypeDefinition.enumOf(
          SpecMilestone.class,
          milestone -> milestone.name().toLowerCase(Locale.ROOT),
          Set.of(SpecMilestone.EIP4844));

  public static <X extends SszData, T extends ObjectAndMetaData<X>>
      ResponseContentTypeDefinition<T> sszResponseType() {
    return new OctetStreamResponseContentTypeDefinition<>(
        (data, out) -> data.getData().sszSerialize(out),
        value -> getSszHeaders(__ -> value.getMilestone(), value.getData()));
  }

  public static <T extends SszData> ResponseContentTypeDefinition<T> sszResponseType(
      final Function<T, SpecMilestone> milestoneSelector) {
    return new OctetStreamResponseContentTypeDefinition<>(
        SszData::sszSerialize, value -> getSszHeaders(milestoneSelector, value));
  }

  @NotNull
  private static <T extends SszData> Map<String, String> getSszHeaders(
      final Function<T, SpecMilestone> milestoneSelector, final T value) {
    return Map.of(
        RestApiConstants.HEADER_CONSENSUS_VERSION,
        Version.fromMilestone(milestoneSelector.apply(value)).name(),
        RestApiConstants.HEADER_CONTENT_DISPOSITION,
        getSszFilename(value));
  }

  private static <T extends SszData> String getSszFilename(final T value) {
    return String.format(
        "filename=\"%s%s.ssz\"",
        value.getSchema().getName().map(name -> name + "-").orElse(""),
        value.hashTreeRoot().toUnprefixedHexString());
  }
}
