package tech.pegasys.artemis.util.backing.view;

import com.google.common.primitives.UnsignedLong;
import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes32;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.type.ListViewTypeBasic;

public class BasicListViews {

  public static class BytesListView extends PackedListView<Byte> {

    public BytesListView(ListViewTypeBasic.BytesListType type, TreeNode node) {
      super(type, node);
    }

    @Override
    Byte decode(Bytes32 chunk, int internalIndex) {
      return chunk.get(internalIndex);
    }

    @Override
    Bytes32 encode(Bytes32 originalChunk, int internalIndex, Byte value) {
      byte[] bytes = originalChunk.toArray();
      bytes[internalIndex] = value;
      return Bytes32.wrap(bytes);
    }
  }

  public static class UInt64ListView extends PackedListView<UnsignedLong> {

    public UInt64ListView(ListViewTypeBasic.UInt64ListType type, TreeNode node) {
      super(type, node);
    }

    @Override
    Bytes32 encode(Bytes32 originalChunk, int internalIndex, UnsignedLong value) {
      return Bytes32.wrap(Bytes.concatenate(
          originalChunk.slice(0, internalIndex * 8),
          Bytes.ofUnsignedLong(value.longValue(), ByteOrder.LITTLE_ENDIAN),
          originalChunk.slice((internalIndex + 1) * 8)
      ));
    }

    @Override
    UnsignedLong decode(Bytes32 chunk, int internalIndex) {
      return UnsignedLong
          .valueOf(chunk.slice(internalIndex * 8, 8).toLong(ByteOrder.LITTLE_ENDIAN));
    }
  }

  public static class BitListView extends PackedListView<Boolean> {

    public BitListView(ListViewTypeBasic.BitListType type, TreeNode node) {
      super(type, node);
    }

    @Override
    Bytes32 encode(Bytes32 originalChunk, int internalIndex, Boolean value) {
      MutableBytes32 dest = originalChunk.mutableCopy();
      int byteIndex = internalIndex / 8;
      int bitIndex = internalIndex % 8;
      byte b = dest.get(byteIndex);
      if (value) {
        b |= 1 << (8 - bitIndex);
      } else {
        b &= ~(1 << (8 - bitIndex));
      }
      dest.set(byteIndex, b);
      return dest;
    }

    @Override
    Boolean decode(Bytes32 chunk, int internalIndex) {
      int byteIndex = internalIndex / 8;
      int bitIndex = internalIndex % 8;
      return (chunk.get(byteIndex) & (1 << (8 - bitIndex))) != 0;
    }
  }
}
