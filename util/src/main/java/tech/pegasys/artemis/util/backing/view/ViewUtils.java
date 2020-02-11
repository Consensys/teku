/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.util.backing.view;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.backing.ListViewRead;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.VectorViewRead;
import tech.pegasys.artemis.util.backing.VectorViewWrite;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.BitView;
import tech.pegasys.artemis.util.backing.view.BasicViews.ByteView;

public class ViewUtils {

  public static VectorViewRead<ByteView> createVectorFromBytes(Bytes bytes) {
    VectorViewType<ByteView> type = new VectorViewType<>(BasicViewTypes.BYTE_TYPE, bytes.size());
    // TODO optimize
    VectorViewWrite<ByteView> ret = type.createDefault().createWritableCopy();
    for (int i = 0; i < bytes.size(); i++) {
      ret.set(i, new ByteView(bytes.get(i)));
    }
    return ret.commitChanges();
  }

  public static Bytes getAllBytes(VectorViewRead<ByteView> vector) {
    // TODO optimize
    MutableBytes bytes = MutableBytes.create((int) vector.getType().getMaxLength());
    for (int i = 0; i < bytes.size(); i++) {
      bytes.set(i, vector.get(i).get());
    }
    return bytes;
  }

  public static ListViewRead<BitView> createBitlistView(Bitlist bitlist) {
    ListViewWrite<BitView> viewWrite =
        new ListViewType<BitView>(BasicViewTypes.BIT_TYPE, bitlist.getMaxSize())
            .createDefault()
            .createWritableCopy();
    for (int i = 0; i < bitlist.getCurrentSize(); i++) {
      viewWrite.append(new BitView(bitlist.getBit(i) > 0));
    }
    return viewWrite.commitChanges();
  }

  public static Bitlist getBitlist(ListViewRead<BitView> bitlistView) {
    Bitlist ret = new Bitlist(bitlistView.size(), bitlistView.getType().getMaxLength());
    for (int i = 0; i < bitlistView.size(); i++) {
      if (bitlistView.get(i).get()) {
        ret.setBit(i);
      }
    }
    return ret;
  }
}
