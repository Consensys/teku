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

package tech.pegasys.teku.beaconrestapi;

import java.util.Map;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.ResponseContentTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class EthereumTypes {

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

  public static final DeserializableTypeDefinition<SpecMilestone> SPEC_VERSION_TYPE =
      DeserializableTypeDefinition.enumOf(SpecMilestone.class);

  public static final DeserializableTypeDefinition<Version> VERSION_TYPE =
      DeserializableTypeDefinition.enumOf(Version.class);

  public static <X extends SszData, T extends ObjectAndMetaData<X>>
      ResponseContentTypeDefinition<T> sszResponseType() {
    return new OctetStreamResponseContentTypeDefinition<>(
        (data, out) -> data.getData().sszSerialize(out),
        value ->
            Map.of(
                RestApiConstants.HEADER_CONSENSUS_VERSION,
                Version.fromMilestone(value.getMilestone()).name(),
                RestApiConstants.HEADER_CONTENT_DISPOSITION,
                String.format(
                    "filename=\"%s%s.ssz\"",
                    value.getData().getSchema().getName().map(name -> name + "-").orElse(""),
                    value.getData().hashTreeRoot().toUnprefixedHexString())));
  }

  public static <T extends SszData> ResponseContentTypeDefinition<T> sszResponseType(
      final Function<T, SpecMilestone> milestoneSelector) {
    return new OctetStreamResponseContentTypeDefinition<>(
        SszData::sszSerialize,
        value ->
            Map.of(
                RestApiConstants.HEADER_CONSENSUS_VERSION,
                Version.fromMilestone(milestoneSelector.apply(value)).name(),
                RestApiConstants.HEADER_CONTENT_DISPOSITION,
                String.format(
                    "filename=\"%s%s.ssz\"",
                    value.getSchema().getName().map(name -> name + "-").orElse(""),
                    value.hashTreeRoot().toUnprefixedHexString())));
  }
}
