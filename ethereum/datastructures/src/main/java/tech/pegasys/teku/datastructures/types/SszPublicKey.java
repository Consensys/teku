package tech.pegasys.teku.datastructures.types;

import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ssz.backing.collections.SszByteVectorImpl;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class SszPublicKey extends SszByteVectorImpl {

  private BLSPublicKey publicKey;

  public SszPublicKey(BLSPublicKey publicKey) {
    super(SszPublicKeySchema.INSTANCE, publicKey.toBytesCompressed());
    this.publicKey = publicKey;
  }

  public SszPublicKey(Supplier<TreeNode> lazyBackingNode) {
    super(SszPublicKeySchema.INSTANCE, lazyBackingNode);
  }

  public BLSPublicKey getBLSPublicKey() {
    return publicKey;
  }
}
