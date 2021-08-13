/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateTest;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.SszVector;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.SszSchema.BackingNodeSource;
import tech.pegasys.teku.ssz.schema.SszSchema.BackingNodeStore;
import tech.pegasys.teku.ssz.schema.SszSchema.CompressedBranchInfo;
import tech.pegasys.teku.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.tree.BranchNode;
import tech.pegasys.teku.ssz.tree.LeafDataNode;
import tech.pegasys.teku.ssz.tree.SszSuperNode;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeUtil;
import tech.pegasys.teku.ssz.tree.TreeUtil.ZeroBranchNode;

public class BeaconStatePhase0Test
    extends AbstractBeaconStateTest<BeaconStatePhase0, MutableBeaconStatePhase0> {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetPhase0();
  }

  @Override
  protected BeaconStateSchema<BeaconStatePhase0, MutableBeaconStatePhase0> getSchema(
      final SpecConfig specConfig) {
    return BeaconStateSchemaPhase0.create(specConfig);
  }

  @Override
  protected BeaconStatePhase0 randomState() {
    return dataStructureUtil.stateBuilderPhase0().build();
  }

  @Test
  void shouldRoundTripVectorWithPowerOfTwoLength() {
    final SszVectorSchema<Checkpoint, ?> schema = SszVectorSchema.create(Checkpoint.SSZ_SCHEMA, 4);
    final SszVector<Checkpoint> data =
        schema.createFromElements(
            List.of(
                new Checkpoint(UInt64.valueOf(1), Bytes32.fromHexString("0x1111")),
                new Checkpoint(UInt64.valueOf(2), Bytes32.fromHexString("0x2222")),
                new Checkpoint(UInt64.valueOf(3), Bytes32.fromHexString("0x3333"))));

    final ByteArrayOutputStream expectedBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(expectedBytes), data.getBackingNode(), "");

    final BackingNodeSource nodeSource = captureNodes(data.getBackingNode());
    final TreeNode rebuiltTree = data.getSchema().loadBackingNodes(nodeSource, data.hashTreeRoot());
    final ByteArrayOutputStream actualBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(actualBytes), rebuiltTree, "");

    assertThat(actualBytes.toString()).isEqualTo(expectedBytes.toString());
    assertThat(rebuiltTree.hashTreeRoot()).isEqualTo(data.hashTreeRoot());
  }

  @Test
  void shouldRoundTripVectorWithNonPowerOfTwoLength() {
    final SszVectorSchema<Checkpoint, ?> schema = SszVectorSchema.create(Checkpoint.SSZ_SCHEMA, 3);
    final SszVector<Checkpoint> data =
        schema.createFromElements(
            List.of(
                new Checkpoint(UInt64.valueOf(1), Bytes32.fromHexString("0x1111")),
                new Checkpoint(UInt64.valueOf(2), Bytes32.fromHexString("0x2222")),
                new Checkpoint(UInt64.valueOf(3), Bytes32.fromHexString("0x3333"))));

    final ByteArrayOutputStream expectedBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(expectedBytes), data.getBackingNode(), "");

    final BackingNodeSource nodeSource = captureNodes(data.getBackingNode());
    final TreeNode rebuiltTree = data.getSchema().loadBackingNodes(nodeSource, data.hashTreeRoot());
    final ByteArrayOutputStream actualBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(actualBytes), rebuiltTree, "");

    assertThat(actualBytes.toString()).isEqualTo(expectedBytes.toString());
    assertThat(rebuiltTree.hashTreeRoot()).isEqualTo(data.hashTreeRoot());
  }

  @Test
  void shouldRoundTripBeaconBlock() {
    final SszData data = dataStructureUtil.randomSignedBeaconBlock(5);

    final ByteArrayOutputStream expectedBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(expectedBytes), data.getBackingNode(), "");

    final BackingNodeSource nodeSource = captureNodes(data.getBackingNode());
    final TreeNode rebuiltTree = data.getSchema().loadBackingNodes(nodeSource, data.hashTreeRoot());
    final ByteArrayOutputStream actualBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(actualBytes), rebuiltTree, "");

    assertThat(actualBytes.toString()).isEqualTo(expectedBytes.toString());
    assertThat(rebuiltTree.hashTreeRoot()).isEqualTo(data.hashTreeRoot());
  }

  @Test
  void shouldRoundTripBeaconStateValidators() {
    final SszData data = dataStructureUtil.randomBeaconState().getValidators();

    final ByteArrayOutputStream expectedBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(expectedBytes), data.getBackingNode(), "");

    final BackingNodeSource nodeSource = captureNodes(data.getBackingNode());
    final TreeNode rebuiltTree = data.getSchema().loadBackingNodes(nodeSource, data.hashTreeRoot());
    final ByteArrayOutputStream actualBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(actualBytes), rebuiltTree, "");

    assertThat(actualBytes.toString()).isEqualTo(expectedBytes.toString());
    assertThat(rebuiltTree.hashTreeRoot()).isEqualTo(data.hashTreeRoot());
    assertThat(data.getSchema().createFromBackingNode(rebuiltTree).toString())
        .isEqualTo(data.toString());
  }

  @Test
  void shouldRoundTripBeaconState() {
    final SszData data = dataStructureUtil.randomBeaconState();

    final ByteArrayOutputStream expectedBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(expectedBytes), data.getBackingNode(), "");

    final BackingNodeSource nodeSource = captureNodes(data.getBackingNode());
    final TreeNode rebuiltTree = data.getSchema().loadBackingNodes(nodeSource, data.hashTreeRoot());
    final ByteArrayOutputStream actualBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(actualBytes), rebuiltTree, "");

    assertThat(actualBytes.toString()).isEqualTo(expectedBytes.toString());
    assertThat(rebuiltTree.hashTreeRoot()).isEqualTo(data.hashTreeRoot());
    assertThat(data.getSchema().createFromBackingNode(rebuiltTree).toString())
        .isEqualTo(data.toString());
  }

  @Test
  void shouldRoundTripMainnetGenesisState() throws Exception {
    final SszData data =
        dataStructureUtil
            .getSpec()
            .deserializeBeaconState(
                Bytes.wrap(
                    Files.readAllBytes(
                        Path.of(
                            "/Users/aj/Documents/code/teku/ethereum/networks/src/main/resources/tech/pegasys/teku/networks/mainnet-genesis.ssz"))));

    final ByteArrayOutputStream expectedBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(expectedBytes), data.getBackingNode(), "");

    final BackingNodeSource nodeSource = captureNodes(data.getBackingNode());
    final TreeNode rebuiltTree = data.getSchema().loadBackingNodes(nodeSource, data.hashTreeRoot());
    final ByteArrayOutputStream actualBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(actualBytes), rebuiltTree, "");

    assertThat(actualBytes.toString()).isEqualTo(expectedBytes.toString());
    assertThat(rebuiltTree.hashTreeRoot()).isEqualTo(data.hashTreeRoot());
    assertThat(data.getSchema().createFromBackingNode(rebuiltTree).toString())
        .isEqualTo(data.toString());
  }

  @Test
  void shouldRoundTripListWithNonPowerOfTwoLength() {
    final SszListSchema<Checkpoint, ?> schema = SszListSchema.create(Checkpoint.SSZ_SCHEMA, 5);
    final SszList<Checkpoint> data =
        schema.createFromElements(
            List.of(
                new Checkpoint(UInt64.valueOf(1), Bytes32.fromHexString("0x1111")),
                new Checkpoint(UInt64.valueOf(2), Bytes32.fromHexString("0x2222")),
                new Checkpoint(UInt64.valueOf(3), Bytes32.fromHexString("0x3333"))));

    final ByteArrayOutputStream expectedBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(expectedBytes), data.getBackingNode(), "");

    final BackingNodeSource nodeSource = captureNodes(data.getBackingNode());
    final TreeNode rebuiltTree = data.getSchema().loadBackingNodes(nodeSource, data.hashTreeRoot());
    final ByteArrayOutputStream actualBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(actualBytes), rebuiltTree, "");

    assertThat(actualBytes.toString()).isEqualTo(expectedBytes.toString());
    assertThat(rebuiltTree.hashTreeRoot()).isEqualTo(data.hashTreeRoot());
  }

  @Test
  void shouldStoreVectorWithNonPowerOfTwoLength() {
    final SszVectorSchema<Checkpoint, ?> schema = SszVectorSchema.create(Checkpoint.SSZ_SCHEMA, 3);
    final SszVector<Checkpoint> data =
        schema.createFromElements(
            List.of(
                new Checkpoint(UInt64.valueOf(1), Bytes32.fromHexString("0x1111")),
                new Checkpoint(UInt64.valueOf(2), Bytes32.fromHexString("0x2222")),
                new Checkpoint(UInt64.valueOf(3), Bytes32.fromHexString("0x3333"))));

    final ByteArrayOutputStream expectedBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(expectedBytes), data.getBackingNode(), "");

    data.getSchema()
        .storeBackingNodes(
            data.getBackingNode(),
            new BackingNodeStore() {
              @Override
              public void storeCompressedBranch(
                  final Bytes32 root, final int depth, final Bytes32[] children) {
                System.out.println(
                    "Branch: " + root + " - " + depth + " - " + Arrays.toString(children));
              }

              @Override
              public void storeLeafNode(final LeafDataNode node) {
                System.out.println("Leaf: " + node.hashTreeRoot() + " - " + node.getData());
              }
            });
  }

  @Test
  void shouldStoreNotFullList() {
    final SszListSchema<Checkpoint, ?> schema = SszListSchema.create(Checkpoint.SSZ_SCHEMA, 5000);
    final SszList<Checkpoint> data =
        schema.createFromElements(
            List.of(
                new Checkpoint(UInt64.valueOf(1), Bytes32.fromHexString("0x1111")),
                new Checkpoint(UInt64.valueOf(2), Bytes32.fromHexString("0x2222")),
                new Checkpoint(UInt64.valueOf(3), Bytes32.fromHexString("0x3333"))));

    final ByteArrayOutputStream expectedBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(expectedBytes), data.getBackingNode(), "");

    data.getSchema()
        .storeBackingNodes(
            data.getBackingNode(),
            new BackingNodeStore() {
              @Override
              public void storeCompressedBranch(
                  final Bytes32 root, final int depth, final Bytes32[] children) {
                System.out.println(
                    "Branch: " + root + " - " + depth + " - " + Arrays.toString(children));
              }

              @Override
              public void storeLeafNode(final LeafDataNode node) {
                if (node.getData().size() > Bytes32.SIZE) {
                  System.out.println("Leaf: " + node.hashTreeRoot() + " - " + node.getData());
                }
              }
            });
  }

  @Test
  void shouldStoreState() throws Exception {
    final BeaconState mainNetState =
        dataStructureUtil
            .getSpec()
            .deserializeBeaconState(
                Bytes.wrap(
                    Files.readAllBytes(
                        Paths.get("/Users/aj/Documents/code/teku/tmp/mainnet.ssz"))));
    //    final SszBitlist simpleBitlist =
    //        SszBitlistSchema.create(250).of(true, true, true, true, true, true, true, true, true,
    // false, false, true, true,true,true,true,true,true,true,true,true,true,true,true, false,
    // false, false, false, false, false, false, false, false, false, false, false, false, false,
    // false, false, false, false, false, false, false, false, false, false, false, false, false,
    // false, false, false, false, false, false, false, false, false, false, false, false, false,
    // false, false);
    final SszData data = mainNetState;
    //    final SszData data = simpleBitlist;

    //    final SszListSchema<Checkpoint, ?> schema = SszListSchema.create(Checkpoint.SSZ_SCHEMA,
    // 3);
    //    final SszList<Checkpoint> data =
    //        schema.createFromElements(
    //            List.of(
    //                new Checkpoint(UInt64.valueOf(1), Bytes32.fromHexString("0x1111")),
    //                new Checkpoint(UInt64.valueOf(2), Bytes32.fromHexString("0x2222")),
    //                new Checkpoint(UInt64.valueOf(3), Bytes32.fromHexString("0x3333"))));
    final ByteArrayOutputStream expectedBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(expectedBytes), data.getBackingNode(), "");

    final AtomicLong storedBytes = new AtomicLong();
    final AtomicLong capturedBranchNodes = new AtomicLong();

    final Map<Bytes32, Bytes> branchNodes = new HashMap<>();
    final Map<Bytes32, Bytes> leafNodes = new HashMap<>();
    data.getSchema()
        .storeBackingNodes(
            data.getBackingNode(),
            new BackingNodeStore() {
              @Override
              public void storeCompressedBranch(
                  final Bytes32 root, final int depth, final Bytes32[] children) {
                if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(root)) {
                  return;
                }
                //                if (children.length > 256) {
                //                  System.out.println("WARN: Serializing a lot of children");
                //                }
                storedBytes.addAndGet(
                    root.size() + children.length * (long) Bytes32.SIZE + UInt64.BYTES);
                capturedBranchNodes.incrementAndGet();
                branchNodes.put(root, CompressedBranchInfo.serialize(depth, children));
                //                System.out.println("Branch: " + root + " - " + depth + " - " +
                // children.length);
              }

              @Override
              public void storeLeafNode(final LeafDataNode node) {
                if (node.getData().size() > Bytes32.SIZE
                    && !TreeUtil.ZERO_TREES_BY_ROOT.containsKey(node.hashTreeRoot())) {
                  storedBytes.addAndGet(node.getData().size());
                  // System.out.println("Leaf: " + node.hashTreeRoot() + " - " + node.getData());
                  leafNodes.put(node.hashTreeRoot(), node.getData());
                }
              }
            });

    final BackingNodeSource nodeSource =
        new BackingNodeSource() {
          @Override
          public CompressedBranchInfo getBranchData(final Bytes32 root) {
            return CompressedBranchInfo.deserialize(branchNodes.get(root));
          }

          @Override
          public Bytes getLeafData(final Bytes32 root) {
            return leafNodes.getOrDefault(root, root);
          }
        };
    System.out.println(
        "Stored: "
            + format(storedBytes.get())
            + " (ssz length: "
            + format(data.sszSerialize().size())
            + ")");
    System.out.println(
        "Captured " + format(capturedBranchNodes.get()) + " compressed branch nodes");
    captureNodes(data.getBackingNode());

    final TreeNode rebuiltTree = data.getSchema().loadBackingNodes(nodeSource, data.hashTreeRoot());
    System.out.println("Rebuilt tree: " + rebuiltTree.hashTreeRoot());
    final ByteArrayOutputStream actualBytes = new ByteArrayOutputStream();
    printTree(new PrintStream(actualBytes), rebuiltTree, "");
    System.out.println("Printed actual tree");

    assertThat(actualBytes.toString()).isEqualTo(expectedBytes.toString());
    System.out.println("Check 2");
    assertThat(rebuiltTree.hashTreeRoot()).isEqualTo(data.hashTreeRoot());
    System.out.println("Check 3");
    final SszData rebuiltData = data.getSchema().createFromBackingNode(rebuiltTree);
    assertThat(rebuiltData.toString()).isEqualTo(data.toString());
  }

  @Test
  void shouldRoundTripCompressedBranchInfo() {
    final CompressedBranchInfo orig =
        new CompressedBranchInfo(
            2341,
            new Bytes32[] {
              dataStructureUtil.randomBytes32(),
              dataStructureUtil.randomBytes32(),
              dataStructureUtil.randomBytes32(),
              dataStructureUtil.randomBytes32(),
              dataStructureUtil.randomBytes32(),
              dataStructureUtil.randomBytes32(),
              dataStructureUtil.randomBytes32(),
            });
    final Bytes data = CompressedBranchInfo.serialize(orig.getDepth(), orig.getChildren());
    System.out.println(data);
    final CompressedBranchInfo result = CompressedBranchInfo.deserialize(data);
    assertThat(result).isEqualToComparingFieldByField(orig);
  }

  private String format(final long number) {
    return NumberFormat.getIntegerInstance().format(number);
  }

  private void printTree(final PrintStream out, final TreeNode node, final String indent) {
    final String depth = "(" + (indent.length() / 2) + ")" + " ";
    if (TreeUtil.ZERO_TREES_BY_ROOT.containsKey(node.hashTreeRoot())) {
      out.println(
          indent
              + depth
              + " "
              + node.hashTreeRoot()
              + ": "
              + TreeUtil.ZERO_TREES_BY_ROOT.get(node.hashTreeRoot()));
    } else if (node instanceof LeafDataNode) {
      out.println(
          indent
              + depth
              + ((LeafDataNode) node).getData()
              + ": "
              + node.hashTreeRoot()
              + ((node instanceof SszSuperNode) ? " SUPER!" : ""));
    } else if (node instanceof ZeroBranchNode) {
      out.println(indent + depth + " " + node.hashTreeRoot() + " (zero branch node)");
    } else if (node instanceof BranchNode) {
      out.println(indent + depth + " " + node.hashTreeRoot());
      final BranchNode branch = (BranchNode) node;
      printTree(out, branch.left(), indent + "  ");
      printTree(out, branch.right(), indent + "  ");
    }
  }

  private BackingNodeSource captureNodes(final TreeNode root) {
    final Map<Bytes32, CompressedBranchInfo> branchData = new HashMap<>();
    final Map<Bytes32, Bytes> leafData = new HashMap<>();

    for (TreeNode node : TreeUtil.ZERO_TREES) {
      captureNode(branchData, leafData, node);
    }
    TreeUtil.iterateNonZero(root, node -> captureNode(branchData, leafData, node));

    final long capturedSize =
        (branchData.size() * 3L * Bytes32.SIZE)
            + leafData.entrySet().stream()
                .mapToLong(entry -> entry.getKey().size() + entry.getValue().size())
                .sum();
    System.out.println("Captured " + format(capturedSize) + " bytes with all branches");
    System.out.println("Captured " + format(branchData.size()) + " branch nodes");
    return new BackingNodeSource() {
      @Override
      public CompressedBranchInfo getBranchData(final Bytes32 root) {
        return checkNotNull(branchData.get(root), "Missing branch " + root);
      }

      @Override
      public Bytes getLeafData(final Bytes32 root) {
        return checkNotNull(leafData.get(root), "Missing leaf " + root);
      }
    };
  }

  private void captureNode(
      final Map<Bytes32, CompressedBranchInfo> branchData,
      final Map<Bytes32, Bytes> leafData,
      final TreeNode node) {
    final Bytes32 root = node.hashTreeRoot();
    if (node instanceof LeafDataNode) {
      if (branchData.containsKey(root)) {
        throw new IllegalStateException(
            "Adding a leaf with data "
                + ((LeafDataNode) node).getData()
                + " with same hash as a branch "
                + root);
      }
      leafData.put(root, ((LeafDataNode) node).getData());
    } else if (node instanceof BranchNode) {
      final BranchNode branch = (BranchNode) node;
      if (leafData.containsKey(root)) {
        throw new IllegalStateException(
            "Adding a branch with same hash as a leaf: "
                + root
                + " - "
                + leafData.get(root)
                + " Left: "
                + branch.left().hashTreeRoot()
                + " Right: "
                + branch.right().hashTreeRoot());
      }
      branchData.put(
          root,
          new CompressedBranchInfo(
              1, new Bytes32[] {branch.left().hashTreeRoot(), branch.right().hashTreeRoot()}));
    } else {
      throw new IllegalArgumentException("Unknown node type: " + node.getClass());
    }
  }
}
