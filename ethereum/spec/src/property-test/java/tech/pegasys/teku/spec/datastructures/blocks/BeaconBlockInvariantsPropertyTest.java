package tech.pegasys.teku.spec.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.BeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.BlindedBeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.SignedBeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.SignedBlindedBeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.versions.deneb.SignedBlindedBlockContentsSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.versions.deneb.SignedBlockContentsSupplier;

public class BeaconBlockInvariantsPropertyTest {

  @Property
  void extractSlotFromBeaconBlock(
      @ForAll(supplier = BeaconBlockSupplier.class) final BeaconBlock beaconBlock) {
    assertThat(BeaconBlockInvariants.extractBeaconBlockSlot(beaconBlock.sszSerialize()))
        .isEqualTo(beaconBlock.getSlot());
  }

  @Property
  void extractSlotFromBlindedBeaconBlock(
      @ForAll(supplier = BlindedBeaconBlockSupplier.class) final BeaconBlock beaconBlock) {
    assertThat(BeaconBlockInvariants.extractBeaconBlockSlot(beaconBlock.sszSerialize()))
        .isEqualTo(beaconBlock.getSlot());
  }

  @Property
  void extractSlotFromSignedBeaconBlock(
      @ForAll(supplier = SignedBeaconBlockSupplier.class)
          final SignedBeaconBlock signedBeaconBlock) {
    assertThat(BeaconBlockInvariants.extractSignedBeaconBlockSlot(signedBeaconBlock.sszSerialize()))
        .isEqualTo(signedBeaconBlock.getSlot());
  }

  @Property
  void extractSlotFromSignedBlindedBeaconBlock(
      @ForAll(supplier = SignedBlindedBeaconBlockSupplier.class)
          final SignedBeaconBlock signedBlindedBeaconBlock) {
    assertThat(
            BeaconBlockInvariants.extractSignedBeaconBlockSlot(
                signedBlindedBeaconBlock.sszSerialize()))
        .isEqualTo(signedBlindedBeaconBlock.getSlot());
  }

  @Property
  void extractSlotFromSignedBlockContents(
      @ForAll(supplier = SignedBlockContentsSupplier.class)
          final SignedBlockContents signedBlockContents) {
    assertThat(
            BeaconBlockInvariants.extractSignedBeaconBlockSlot(signedBlockContents.sszSerialize()))
        .isEqualTo(signedBlockContents.getSlot());
  }

  @Property
  void extractSlotFromSignedBlindedBlockContents(
      @ForAll(supplier = SignedBlindedBlockContentsSupplier.class)
          final SignedBlindedBlockContents signedBlindedBlockContents) {
    assertThat(
            BeaconBlockInvariants.extractSignedBeaconBlockSlot(
                signedBlindedBlockContents.sszSerialize()))
        .isEqualTo(signedBlindedBlockContents.getSlot());
  }
}
