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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateTest;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.SszVector;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.SszSchema.BackingNodeSource;
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
  void shouldHashDifferently() {
    final Bytes leafData =
        Bytes.fromHexString("0x4b363db94e286120d76eb905340fdd4e54bfe9f06bf33ff6cf5ad27f511bfe95");
    final Bytes leftRoot =
        Bytes.fromHexString("0xaa7437fc4869fc670c3e8255187207c0236ca7478e232b63af461ae2f824d8b1");
    final Bytes rightRoot =
        Bytes.fromHexString("0x4752000000000000000000000000000000000000000000000000000000000000");

    System.out.println(leafData);
    System.out.println(Hash.sha2_256(Bytes.concatenate(leftRoot, rightRoot)));
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
    final Map<Bytes32, Pair<Bytes32, Bytes32>> branchData = new HashMap<>();
    final Map<Bytes32, Bytes> leafData = new HashMap<>();

    for (TreeNode node : TreeUtil.ZERO_TREES) {
      captureNode(branchData, leafData, node);
    }
    TreeUtil.iterateNonZero(root, node -> captureNode(branchData, leafData, node));

    return new BackingNodeSource() {
      @Override
      public Pair<Bytes32, Bytes32> getBranchData(final Bytes32 root) {
        return checkNotNull(branchData.get(root), "Missing branch " + root);
      }

      @Override
      public Bytes getLeafData(final Bytes32 root) {
        return checkNotNull(leafData.get(root), "Missing leaf " + root);
      }
    };
  }

  private void captureNode(
      final Map<Bytes32, Pair<Bytes32, Bytes32>> branchData,
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
      branchData.put(root, Pair.of(branch.left().hashTreeRoot(), branch.right().hashTreeRoot()));
    } else {
      throw new IllegalArgumentException("Unknown node type: " + node.getClass());
    }
  }
}
