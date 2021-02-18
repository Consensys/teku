package tech.pegasys.teku.datastructures.types;

import tech.pegasys.teku.ssz.backing.schema.collections.SszByteVectorSchemaImpl;

public class SszPublicKeySchema extends SszByteVectorSchemaImpl<SszPublicKey> {

  public static final SszPublicKeySchema INSTANCE = new SszPublicKeySchema();

  private SszPublicKeySchema() {
    super(48);
  }
}
