package tech.pegasys.teku.statetransition.block;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@FunctionalInterface
public interface BlockInProductionProvider {
   boolean blockInProduction(UInt64 slot);
}
