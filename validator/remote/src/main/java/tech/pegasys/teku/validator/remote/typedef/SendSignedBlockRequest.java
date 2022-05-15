package tech.pegasys.teku.validator.remote.typedef;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

import java.util.Collections;
import java.util.Map;

import static tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions.SSZ_NONE_TYPE_DEFINITION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_BLINDED_BLOCK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_BLOCK;

public class SendSignedBlockRequest extends AbstractTypeDefRequest {
  private final boolean preferOctetStream;

  public SendSignedBlockRequest(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient, final boolean preferOctetStream) {
    super(baseEndpoint, okHttpClient);
    this.preferOctetStream = preferOctetStream;
  }

  public SendSignedBlockResult sendSignedBlock(SignedBeaconBlock signedBeaconBlock) {
    final ValidatorApiMethod apiMethod =
        signedBeaconBlock.getMessage().getBody().isBlinded()
            ? SEND_SIGNED_BLINDED_BLOCK
            : SEND_SIGNED_BLOCK;

    if (preferOctetStream) {
      return postOctetStream(apiMethod,
          Collections.emptyMap(),
          signedBeaconBlock.sszSerialize().toArray(),
          new ResponseHandler<>(SSZ_NONE_TYPE_DEFINITION))
          .map(__ -> SendSignedBlockResult.success(Bytes32.ZERO))
          .orElseGet(() -> SendSignedBlockResult.notImported("UNKNOWN"));
    }

    return postJson(apiMethod,
        Collections.emptyMap(),
        signedBeaconBlock,
        signedBeaconBlock.getSchema().getJsonTypeDefinition(),
        new ResponseHandler<>(SSZ_NONE_TYPE_DEFINITION))
        .map(__ -> SendSignedBlockResult.success(Bytes32.ZERO))
        .orElseGet(() -> SendSignedBlockResult.notImported("UNKNOWN"));

  }
}
