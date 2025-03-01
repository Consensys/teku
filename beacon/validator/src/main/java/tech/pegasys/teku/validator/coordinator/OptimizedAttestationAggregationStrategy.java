/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.validator.coordinator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;

/**
 * An optimized strategy for selecting and aggregating attestations for inclusion in beacon blocks.
 * This strategy aims to maximize the number of unique validators included in a block while
 * respecting the block size constraints.
 */
public class OptimizedAttestationAggregationStrategy {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final AggregatingAttestationPool attestationPool;

  public OptimizedAttestationAggregationStrategy(
      final Spec spec, final AggregatingAttestationPool attestationPool) {
    this.spec = spec;
    this.attestationPool = attestationPool;
  }

  /**
   * Selects attestations for inclusion in a block using an optimized strategy that maximizes
   * validator participation while minimizing block space usage.
   *
   * @param blockSlotState The beacon state at the block's slot
   * @return A list of optimally selected attestations for inclusion in the block
   */
  public List<Attestation> selectOptimizedAttestations(final BeaconState blockSlotState) {
    final UInt64 slot = blockSlotState.getSlot();
    final int maxAttestations = spec.getSpecConfig(slot).getMaxAttestations().intValue();
    
    // Get all valid attestations from the pool
    final List<Attestation> validAttestations = 
        attestationPool.getAttestationsForBlock(
            blockSlotState, new AttestationForkChecker(spec, blockSlotState));
    
    if (validAttestations.isEmpty()) {
      return validAttestations;
    }
    
    // Group attestations by attestation data
    final Map<AttestationData, List<Attestation>> attestationsByData = new HashMap<>();
    for (Attestation attestation : validAttestations) {
      attestationsByData
          .computeIfAbsent(attestation.getData(), __ -> new ArrayList<>())
          .add(attestation);
    }
    
    // For each attestation data, find the optimal aggregation
    final List<Attestation> optimizedAttestations = new ArrayList<>();
    for (Map.Entry<AttestationData, List<Attestation>> entry : attestationsByData.entrySet()) {
      final AttestationData data = entry.getKey();
      final List<Attestation> attestationsWithSameData = entry.getValue();
      
      // If there's only one attestation for this data, add it directly
      if (attestationsWithSameData.size() == 1) {
        optimizedAttestations.add(attestationsWithSameData.get(0));
        continue;
      }
      
      // Find the optimal aggregation for this attestation data
      final Attestation optimalAggregation = findOptimalAggregation(attestationsWithSameData, blockSlotState);
      optimizedAttestations.add(optimalAggregation);
    }
    
    // Sort the optimized attestations by priority
    final List<Attestation> sortedAttestations = sortAttestationsByPriority(
        optimizedAttestations, blockSlotState);
    
    // Get the final list of attestations to include (limited by maxAttestations)
    final List<Attestation> finalAttestations = sortedAttestations.stream()
        .limit(maxAttestations)
        .collect(Collectors.toList());
    
    // Log performance metrics
    logAttestationOptimizationMetrics(blockSlotState, validAttestations, finalAttestations);
    
    return finalAttestations;
  }
  
  /**
   * Finds the optimal aggregation of attestations with the same attestation data.
   * The goal is to maximize the number of validators included while minimizing redundancy.
   *
   * @param attestations List of attestations with the same attestation data
   * @param blockSlotState The beacon state at the block's slot
   * @return The optimal aggregation of the given attestations
   */
  private Attestation findOptimalAggregation(
      final List<Attestation> attestations, final BeaconState blockSlotState) {
    // If there's only one attestation, return it directly
    if (attestations.size() == 1) {
      return attestations.get(0);
    }
    
    // Sort attestations by the number of aggregation bits (descending)
    attestations.sort(
        Comparator.comparingInt((Attestation a) -> a.getAggregationBits().getBitCount()).reversed());
    
    // Start with the attestation that has the most aggregation bits
    Attestation bestAggregation = attestations.get(0);
    SszBitlist aggregatedBits = bestAggregation.getAggregationBits();
    
    // Try to add more attestations if they contribute new validators
    for (int i = 1; i < attestations.size(); i++) {
      final Attestation currentAttestation = attestations.get(i);
      final SszBitlist currentBits = currentAttestation.getAggregationBits();
      
      // Count how many new validators this attestation would add
      int newValidators = 0;
      for (int j = 0; j < currentBits.size(); j++) {
        if (currentBits.getBit(j) && !aggregatedBits.getBit(j)) {
          newValidators++;
        }
      }
      
      // If this attestation adds new validators, merge it with our best aggregation
      if (newValidators > 0) {
        // Create a new bitlist that combines both attestations
        final SszBitlist newAggregatedBits = aggregatedBits.or(currentBits);
        
        // Create a new attestation with the combined bitlist
        bestAggregation = new Attestation(
            newAggregatedBits,
            bestAggregation.getData(),
            bestAggregation.getSignature());
        
        // Update the aggregated bits for the next iteration
        aggregatedBits = newAggregatedBits;
      }
    }
    
    return bestAggregation;
  }
  
  /**
   * Sorts attestations by priority for inclusion in a block.
   * Priority is based on a combination of aggregation count and inclusion delay.
   *
   * @param attestations List of attestations to sort
   * @param blockSlotState The beacon state at the block's slot
   * @return Sorted list of attestations by priority (highest first)
   */
  private List<Attestation> sortAttestationsByPriority(
      final List<Attestation> attestations, final BeaconState blockSlotState) {
    final UInt64 slot = blockSlotState.getSlot();
    final UInt64 attestationInclusionRange = spec.getSpecConfig(slot).getMaxAttestationInclusionDelay();
    
    return attestations.stream()
        .sorted((a1, a2) -> {
          // Calculate inclusion delay (higher delay = higher priority as they're about to expire)
          final int delay1 = slot.minus(a1.getData().getSlot()).intValue();
          final int delay2 = slot.minus(a2.getData().getSlot()).intValue();
          final int maxDelay = attestationInclusionRange.intValue();
          
          // Normalize delay to a priority score (attestations about to expire get higher priority)
          final double delayPriority1 = (double) delay1 / maxDelay;
          final double delayPriority2 = (double) delay2 / maxDelay;
          
          // Get aggregation counts
          final int aggCount1 = a1.getAggregationBits().getBitCount();
          final int aggCount2 = a2.getAggregationBits().getBitCount();
          
          // Calculate committee sizes
          final int committeeSize1 = spec.getBeaconStateUtil(slot)
              .getBeaconCommitteeSize(blockSlotState, a1.getData().getSlot(), a1.getData().getIndex());
          final int committeeSize2 = spec.getBeaconStateUtil(slot)
              .getBeaconCommitteeSize(blockSlotState, a2.getData().getSlot(), a2.getData().getIndex());
          
          // Normalize aggregation counts as percentage of committee size
          final double aggPercentage1 = (double) aggCount1 / committeeSize1;
          final double aggPercentage2 = (double) aggCount2 / committeeSize2;
          
          // Calculate combined priority score (70% weight to aggregation, 30% to delay)
          final double priority1 = (0.7 * aggPercentage1) + (0.3 * delayPriority1);
          final double priority2 = (0.7 * aggPercentage2) + (0.3 * delayPriority2);
          
          // Sort by priority (higher first)
          return Double.compare(priority2, priority1);
        })
        .collect(Collectors.toList());
  }

  /**
   * Logs metrics about the attestation optimization process to help track performance improvements.
   * 
   * @param blockSlotState The beacon state at the block's slot
   * @param originalAttestations The original list of attestations from the pool
   * @param optimizedAttestations The optimized list of attestations after aggregation and sorting
   */
  private void logAttestationOptimizationMetrics(
      final BeaconState blockSlotState,
      final List<Attestation> originalAttestations,
      final List<Attestation> optimizedAttestations) {
    
    if (originalAttestations.isEmpty()) {
      return;
    }
    
    final UInt64 slot = blockSlotState.getSlot();
    
    // Count unique validators in original attestations
    final Set<Integer> originalValidators = new HashSet<>();
    int originalAttestationCount = 0;
    
    // We'll simulate what would happen with the default selection strategy
    // by taking the first maxAttestations attestations from the original list
    final int maxAttestations = spec.getSpecConfig(slot).getMaxAttestations().intValue();
    final int originalSize = Math.min(originalAttestations.size(), maxAttestations);
    
    for (int i = 0; i < originalSize; i++) {
      final Attestation att = originalAttestations.get(i);
      final SszBitlist bits = att.getAggregationBits();
      
      for (int j = 0; j < bits.size(); j++) {
        if (bits.getBit(j)) {
          originalValidators.add(j);
        }
      }
      originalAttestationCount++;
    }
    
    // Count unique validators in optimized attestations
    final Set<Integer> optimizedValidators = new HashSet<>();
    int optimizedAttestationCount = 0;
    
    for (Attestation att : optimizedAttestations) {
      final SszBitlist bits = att.getAggregationBits();
      
      for (int j = 0; j < bits.size(); j++) {
        if (bits.getBit(j)) {
          optimizedValidators.add(j);
        }
      }
      optimizedAttestationCount++;
    }
    
    // Calculate improvement metrics
    final int originalValidatorCount = originalValidators.size();
    final int optimizedValidatorCount = optimizedValidators.size();
    final double validatorCountImprovement = 
        originalValidatorCount > 0 
            ? ((double)(optimizedValidatorCount - originalValidatorCount) / originalValidatorCount) * 100 
            : 0;
    
    // Calculate average attestation size (number of validators per attestation)
    final double avgOriginalAttSize = 
        originalAttestationCount > 0 ? (double)originalValidatorCount / originalAttestationCount : 0;
    final double avgOptimizedAttSize = 
        optimizedAttestationCount > 0 ? (double)optimizedValidatorCount / optimizedAttestationCount : 0;
    final double avgSizeImprovement = 
        avgOriginalAttSize > 0 ? ((avgOptimizedAttSize - avgOriginalAttSize) / avgOriginalAttSize) * 100 : 0;
    
    // Log the metrics
    LOG.debug(
        "Attestation optimization metrics - Slot: {}, " +
        "Unique validators: {} -> {} ({}% improvement), " +
        "Attestation count: {} -> {}, " +
        "Avg validators per attestation: {} -> {} ({}% improvement)",
        slot,
        originalValidatorCount,
        optimizedValidatorCount,
        String.format("%.2f", validatorCountImprovement),
        originalAttestationCount,
        optimizedAttestationCount,
        String.format("%.2f", avgOriginalAttSize),
        String.format("%.2f", avgOptimizedAttSize),
        String.format("%.2f", avgSizeImprovement));
  }
} 
