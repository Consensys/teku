# Gossip Validator Log Context

## Goal

Make execution payload bid and proposer preferences validation failures identifiable from
both trace logs and `InternalValidationResult` descriptions, without changing validation
behavior.

## Message Format

Every non-accept execution payload bid message starts with:

```text
Execution payload bid (builder index <builderIndex>, slot <slot>, value <value> ETH):
```

Every non-accept proposer preferences message starts with:

```text
Proposer preferences (validator index <validatorIndex>, proposal slot <proposalSlot>, dependent root <dependentRoot>):
```

The validation reason follows the prefix. Rule-specific values remain in the reason when
they help diagnose the failure, including parent block hash and root for head compatibility,
gas limits for gas-limit compatibility, and expected values for index or signature-related
checks.

The same identifying context and reason are emitted by the validator's `LOG.trace` call and
returned in its `InternalValidationResult` description.

## Scope

Update all non-accept paths in:

- `ExecutionPayloadBidGossipValidator`
- `ProposerPreferencesGossipValidator`

Do not change validation result codes, validation order, asynchronous behavior, cache
behavior, or acceptance criteria. Do not introduce MDC or change project-wide logging.

## Testing

Use the existing validator tests to assert the enriched `InternalValidationResult`
descriptions. Add TRACE `LogCaptor` coverage for the previously ambiguous head-branch
compatibility and invalid-signature paths to verify the emitted logs contain the same
identity context and relevant reason details.

Run the two targeted validator test classes and Spotless checks for the statetransition
module.
