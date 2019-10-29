/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.chain.storage;

import org.ethereum.beacon.chain.storage.impl.BeaconBlockStorageImpl;
import org.ethereum.beacon.chain.storage.impl.SerializerFactory;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.hasher.ObjectHasher;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconBlockBody;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.db.Database;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValues;

public class BeaconBlockStorageTest {

  private long counter = 0;

  private BeaconBlockStorage create(BeaconChainSpec spec) {
    return BeaconBlockStorageImpl.create(
        Database.inMemoryDB(),
        ObjectHasher.createSSZOverSHA256(spec.getConstants()),
        SerializerFactory.createSSZ(spec.getConstants()));
  }

  private BeaconBlock createBlock(long slot, BeaconBlock parent, Hash32 parentHash) {
    return new BeaconBlock(
        SlotNumber.of(slot),
        parent == null ? Hash32.ZERO : parentHash,
        Hash32.wrap(Bytes32.leftPad(BytesValues.toMinimalBytes(counter++))),
        BeaconBlockBody.getEmpty(BeaconChainSpec.DEFAULT_CONSTANTS),
        BLSSignature.ZERO);
  }

  // TODO: Test smth
  //
  //  @Test
  //  public void testEmpty1() throws Exception {
  //    BeaconBlockStorage bbs = create();
  //    assertTrue(bbs.isEmpty());
  //    assertEquals(0, bbs.getSlotBlocks(0).size());
  //    assertFalse(bbs.getSlotCanonicalBlock(0).isPresent());
  //    assertEquals(-1, bbs.getMaxSlot());
  //  }
  //
  //  @Test(expected = IllegalStateException.class)
  //  public void testEmpty2() throws Exception {
  //    BeaconBlockStorage bbs = create();
  //    bbs.getCanonicalHead();
  //  }
  //
  //  @Test
  //  public void testGenesis1() throws Exception {
  //    BeaconBlockStorage bbs = create();
  //    BeaconBlock genBlock = createBlock(0, null);
  //    bbs.put(genBlock);
  //
  //    assertFalse(bbs.isEmpty());
  //    assertEquals(genBlock.getHash(), bbs.getCanonicalHead());
  //    assertEquals(0, bbs.getMaxSlot());
  //    assertEquals(1, bbs.getSlotBlocks(0).size());
  //    assertEquals(genBlock.getHash(), bbs.getSlotBlocks(0).get(0));
  //    assertEquals(0, bbs.getSlotBlocks(1).size());
  //    assertEquals(genBlock.getHash(), bbs.getSlotCanonicalBlock(0).get());
  //  }
  //
  //  @Test
  //  public void testSimple1() throws Exception {
  //    BeaconBlockStorage bbs = create();
  //    BeaconBlock genBlock = createBlock(0, null);
  //    bbs.put(genBlock);
  //    BeaconBlock b1 = createBlock(1, genBlock);
  //    bbs.put(b1);
  //
  //    assertEquals(1, bbs.getMaxSlot());
  //    assertEquals(1, bbs.getSlotBlocks(0).size());
  //    assertEquals(1, bbs.getSlotBlocks(1).size());
  //    assertEquals(0, bbs.getSlotBlocks(2).size());
  //    assertEquals(genBlock.getHash(), bbs.getSlotBlocks(0).get(0));
  //    assertEquals(genBlock.getHash(), bbs.getSlotCanonicalBlock(0).get());
  //    assertEquals(b1.getHash(), bbs.getSlotBlocks(1).get(0));
  //    assertEquals(b1.getHash(), bbs.getSlotCanonicalBlock(1).get());
  //    assertEquals(b1.getHash(), bbs.getCanonicalHead());
  //
  //    bbs.reorgTo(b1.getHash());
  //
  //    assertEquals(1, bbs.getMaxSlot());
  //    assertEquals(1, bbs.getSlotBlocks(0).size());
  //    assertEquals(1, bbs.getSlotBlocks(1).size());
  //    assertEquals(0, bbs.getSlotBlocks(2).size());
  //    assertEquals(genBlock.getHash(), bbs.getSlotBlocks(0).get(0));
  //    assertEquals(genBlock.getHash(), bbs.getSlotCanonicalBlock(0).get());
  //    assertEquals(b1.getHash(), bbs.getSlotBlocks(1).get(0));
  //    assertEquals(b1.getHash(), bbs.getSlotCanonicalBlock(1).get());
  //    assertEquals(b1.getHash(), bbs.getCanonicalHead());
  //  }
  //
  //  @Test
  //  public void testSimple2() throws Exception {
  //    BeaconBlockStorage bbs = create();
  //    BeaconBlock genBlock = createBlock(0, null);
  //    bbs.put(genBlock);
  //    BeaconBlock b1 = createBlock(2, genBlock);
  //    bbs.put(b1);
  //
  //    assertEquals(2, bbs.getMaxSlot());
  //    assertEquals(1, bbs.getSlotBlocks(0).size());
  //    assertEquals(0, bbs.getSlotBlocks(1).size());
  //    assertEquals(1, bbs.getSlotBlocks(2).size());
  //    assertEquals(genBlock.getHash(), bbs.getSlotBlocks(0).get(0));
  //    assertEquals(genBlock.getHash(), bbs.getSlotCanonicalBlock(0).get());
  //    assertEquals(0, bbs.getSlotBlocks(1).size());
  //    assertFalse(bbs.getSlotCanonicalBlock(1).isPresent());
  //    assertEquals(b1.getHash(), bbs.getSlotBlocks(2).get(0));
  //    assertEquals(b1.getHash(), bbs.getSlotCanonicalBlock(2).get());
  //    assertEquals(b1.getHash(), bbs.getCanonicalHead());
  //
  //    bbs.reorgTo(b1.getHash());
  //
  //    assertEquals(2, bbs.getMaxSlot());
  //    assertEquals(1, bbs.getSlotBlocks(0).size());
  //    assertEquals(0, bbs.getSlotBlocks(1).size());
  //    assertEquals(1, bbs.getSlotBlocks(2).size());
  //    assertEquals(genBlock.getHash(), bbs.getSlotBlocks(0).get(0));
  //    assertEquals(genBlock.getHash(), bbs.getSlotCanonicalBlock(0).get());
  //    assertEquals(0, bbs.getSlotBlocks(1).size());
  //    assertFalse(bbs.getSlotCanonicalBlock(1).isPresent());
  //    assertEquals(b1.getHash(), bbs.getSlotBlocks(2).get(0));
  //    assertEquals(b1.getHash(), bbs.getSlotCanonicalBlock(2).get());
  //    assertEquals(b1.getHash(), bbs.getCanonicalHead());
  //  }
  //
  //  @Test
  //  public void testFork1() throws Exception {
  //    BeaconBlockStorage bbs = create();
  //    BeaconBlock genBlock = createBlock(0, null);
  //    bbs.put(genBlock);
  //    BeaconBlock b1 = createBlock(1, genBlock);
  //    bbs.put(b1);
  //    BeaconBlock b1_1 = createBlock(1, genBlock);
  //    bbs.put(b1_1);
  //
  //    assertEquals(1, bbs.getMaxSlot());
  //    assertEquals(1, bbs.getSlotBlocks(0).size());
  //    assertEquals(2, bbs.getSlotBlocks(1).size());
  //    assertEquals(0, bbs.getSlotBlocks(2).size());
  //    assertTrue(new HashSet<>(bbs.getSlotBlocks(1)).contains(b1.getHash()));
  //    assertTrue(new HashSet<>(bbs.getSlotBlocks(1)).contains(b1_1.getHash()));
  //    assertEquals(b1.getHash(), bbs.getSlotCanonicalBlock(1).get());
  //    assertEquals(b1.getHash(), bbs.getCanonicalHead());
  //
  //    bbs.reorgTo(b1.getHash());
  //
  //    assertTrue(new HashSet<>(bbs.getSlotBlocks(1)).contains(b1.getHash()));
  //    assertTrue(new HashSet<>(bbs.getSlotBlocks(1)).contains(b1_1.getHash()));
  //    assertEquals(b1.getHash(), bbs.getSlotCanonicalBlock(1).get());
  //    assertEquals(b1.getHash(), bbs.getCanonicalHead());
  //
  //    bbs.reorgTo(b1_1.getHash());
  //
  //    assertTrue(new HashSet<>(bbs.getSlotBlocks(1)).contains(b1.getHash()));
  //    assertTrue(new HashSet<>(bbs.getSlotBlocks(1)).contains(b1_1.getHash()));
  //    assertEquals(b1_1.getHash(), bbs.getSlotCanonicalBlock(1).get());
  //    assertEquals(b1_1.getHash(), bbs.getCanonicalHead());
  //
  //  }
  //
  //  @Test
  //  public void testFork2() throws Exception {
  //    BeaconBlockStorage bbs = create();
  //    BeaconBlock genBlock = createBlock(0, null);
  //    bbs.put(genBlock);
  //    BeaconBlock b1 = createBlock(1, genBlock);
  //    bbs.put(b1);
  //    BeaconBlock b2 = createBlock(2, genBlock);
  //    bbs.put(b2);
  //
  //    assertEquals(2, bbs.getMaxSlot());
  //    assertEquals(1, bbs.getSlotBlocks(0).size());
  //    assertEquals(1, bbs.getSlotBlocks(1).size());
  //    assertEquals(1, bbs.getSlotBlocks(2).size());
  //    assertEquals(genBlock.getHash(), bbs.getSlotCanonicalBlock(0).get());
  //    assertEquals(b1.getHash(), bbs.getSlotCanonicalBlock(1).get());
  //    assertEquals(b1.getHash(), bbs.getCanonicalHead());
  //    assertFalse(bbs.getSlotCanonicalBlock(2).isPresent());
  //
  //    bbs.reorgTo(b1.getHash());
  //
  //    assertEquals(b1.getHash(), bbs.getSlotCanonicalBlock(1).get());
  //    assertEquals(b1.getHash(), bbs.getCanonicalHead());
  //    assertFalse(bbs.getSlotCanonicalBlock(2).isPresent());
  //
  //    bbs.reorgTo(b2.getHash());
  //
  //    assertEquals(genBlock.getHash(), bbs.getSlotCanonicalBlock(0).get());
  //    assertFalse(bbs.getSlotCanonicalBlock(1).isPresent());
  //    assertEquals(b2.getHash(), bbs.getSlotCanonicalBlock(2).get());
  //    assertEquals(b2.getHash(), bbs.getCanonicalHead());
  //
  //    bbs.reorgTo(b1.getHash());
  //
  //    assertEquals(b1.getHash(), bbs.getSlotCanonicalBlock(1).get());
  //    assertEquals(b1.getHash(), bbs.getCanonicalHead());
  //    assertFalse(bbs.getSlotCanonicalBlock(2).isPresent());
  //
  //    BeaconBlock b3 = createBlock(3, b1);
  //    bbs.put(b3);
  //    BeaconBlock b4 = createBlock(4, b2);
  //    bbs.put(b4);
  //
  //    assertEquals(b1.getHash(), bbs.getSlotCanonicalBlock(1).get());
  //    assertFalse(bbs.getSlotCanonicalBlock(2).isPresent());
  //    assertEquals(b3.getHash(), bbs.getSlotCanonicalBlock(3).get());
  //    assertFalse(bbs.getSlotCanonicalBlock(4).isPresent());
  //    assertFalse(bbs.getSlotCanonicalBlock(5).isPresent());
  //    assertEquals(b3.getHash(), bbs.getCanonicalHead());
  //
  //    bbs.reorgTo(b4.getHash());
  //
  //    assertFalse(bbs.getSlotCanonicalBlock(1).isPresent());
  //    assertEquals(b2.getHash(), bbs.getSlotCanonicalBlock(2).get());
  //    assertFalse(bbs.getSlotCanonicalBlock(3).isPresent());
  //    assertEquals(b4.getHash(), bbs.getSlotCanonicalBlock(4).get());
  //    assertFalse(bbs.getSlotCanonicalBlock(5).isPresent());
  //    assertEquals(b4.getHash(), bbs.getCanonicalHead());
  //
  //    bbs.reorgTo(b3.getHash());
  //
  //    assertEquals(b1.getHash(), bbs.getSlotCanonicalBlock(1).get());
  //    assertFalse(bbs.getSlotCanonicalBlock(2).isPresent());
  //    assertEquals(b3.getHash(), bbs.getSlotCanonicalBlock(3).get());
  //    assertFalse(bbs.getSlotCanonicalBlock(4).isPresent());
  //    assertFalse(bbs.getSlotCanonicalBlock(5).isPresent());
  //    assertEquals(b3.getHash(), bbs.getCanonicalHead());
  //  }
}
