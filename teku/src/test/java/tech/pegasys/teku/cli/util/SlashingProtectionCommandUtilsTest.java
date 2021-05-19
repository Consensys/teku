package tech.pegasys.teku.cli.util;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class SlashingProtectionCommandUtilsTest {
  final Spec spec = TestSpecFactory.createMinimalPhase0();

  final UInt64 genesis = UInt64.valueOf(1234);

  @Test
  void getComputedSlot_shouldHandlePreGenesis() {
    assertThat(SlashingProtectionCommandUtils.getComputedSlot(genesis, genesis.decrement(), spec)).isEqualTo(UInt64.ZERO);
  }

  @Test
  void getComputedSlot_shouldHandleGenesis() {
    assertThat(SlashingProtectionCommandUtils.getComputedSlot(genesis, genesis, spec)).isEqualTo(UInt64.ZERO);
  }

  @Test
  void getComputedSlot_shouldHandleAfterGenesis() {
    final UInt64 oneThousandSlotSeconds = UInt64.valueOf(spec.getGenesisSpec().getConfig().getSecondsPerSlot()).times(1000);
    assertThat(SlashingProtectionCommandUtils.getComputedSlot(genesis, genesis.plus(oneThousandSlotSeconds), spec)).isEqualTo(UInt64.valueOf(1000));
  }
}
