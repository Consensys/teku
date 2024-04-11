package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import static tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions.createUInt64Definition;

public abstract class AbstractSszUInt64Schema<T extends SszUInt64>
    extends AbstractSszPrimitiveSchema<UInt64, T> {

  protected AbstractSszUInt64Schema() {
    super(64);
  }

  @Override
  public UInt64 createFromLeafBackingNode(LeafDataNode node, int internalIndex) {
    Bytes leafNodeBytes = node.getData();
    try {
      Bytes elementBytes = leafNodeBytes.slice(internalIndex * 8, 8);
      return UInt64.fromLongBits(elementBytes.toLong(ByteOrder.LITTLE_ENDIAN));
    } catch (Exception e) {
      // additional info to track down the bug https://github.com/PegaSysEng/teku/issues/2579
      String info =
          "Refer to https://github.com/PegaSysEng/teku/issues/2579 if see this exception. ";
      info += "internalIndex = " + internalIndex;
      info += ", leafNodeBytes: " + leafNodeBytes.getClass().getSimpleName();
      try {
        info += ", leafNodeBytes = " + leafNodeBytes.copy();
      } catch (Exception ex) {
        info += "(" + ex + ")";
      }
      try {
        info += ", leafNodeBytes[] = " + Arrays.toString(leafNodeBytes.toArray());
      } catch (Exception ex) {
        info += "(" + ex + ")";
      }
      throw new RuntimeException(info, e);
    }
  }

  @Override
  public TreeNode updateBackingNode(TreeNode srcNode, int index, SszData newValue) {
    Bytes uintBytes =
        Bytes.ofUnsignedLong(((SszUInt64) newValue).longValue(), ByteOrder.LITTLE_ENDIAN);
    Bytes curVal = ((LeafNode) srcNode).getData();
    Bytes newBytes = updateExtending(curVal, index * 8, uintBytes);
    return LeafNode.create(newBytes);
  }

  @Override
  public TreeNode updatePackedNode(
      TreeNode srcNode, List<PackedNodeUpdate<UInt64, T>> updates) {
    if (updates.size() == 4) {
      byte[] data = new byte[32];
      for (int i = 0; i < 4; i++) {
        long longValue = updates.get(i).getNewValue().longValue();
        int off = i * 8;
        data[off + 0] = (byte) longValue;
        data[off + 1] = (byte) (longValue >> 8);
        data[off + 2] = (byte) (longValue >> 16);
        data[off + 3] = (byte) (longValue >> 24);
        data[off + 4] = (byte) (longValue >> 32);
        data[off + 5] = (byte) (longValue >> 40);
        data[off + 6] = (byte) (longValue >> 48);
        data[off + 7] = (byte) (longValue >> 56);
      }
      return LeafNode.create(Bytes.wrap(data));
    } else {
      return super.updatePackedNode(srcNode, updates);
    }
  }

  @Override
  public TreeNode getDefaultTree() {
    return LeafNode.ZERO_LEAVES[8];
  }

  @Override
  public DeserializableTypeDefinition<T> getJsonTypeDefinition() {
    return createUInt64Definition(this);
  }

  @Override
  public String toString() {
    return "UInt64";
  }
}
