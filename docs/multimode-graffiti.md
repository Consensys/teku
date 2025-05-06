# Multimode Graffiti Configuration

Teku now supports multimode graffiti configuration, allowing validators to use different graffiti messages based on various criteria. This is similar to the functionality provided by other clients like Prysm.

## Configuration File Format

The multimode graffiti file uses YAML format and supports the following options:

### Specific Validator Graffiti

You can specify different graffiti messages for individual validators using their public keys:

```yaml
specific:
  a057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a: "Validator 0xa05781 was here"
  b057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a: "Validator 0xb05781 was here"
```

### Ordered Graffiti List

You can provide an ordered list of graffiti messages that will be used in sequence, cycling back to the beginning when it reaches the end:

```yaml
ordered:
  - "First ordered graffiti message"
  - "Second ordered graffiti message"
  - "Third ordered graffiti message"
```

This is particularly useful for creating patterns on the graffiti wall.

### Random Graffiti Selection

You can provide a list of random graffiti messages, and one will be chosen randomly each time:

```yaml
random:
  - "Random message 1"
  - "Random message 2"
  - "Random message 3"
```

### Default Fallback Message

You can specify a default fallback message for when no other options apply:

```yaml
defaultGraffiti: "Default fallback graffiti message"
```

## Order of Precedence

When determining which graffiti to use, Teku follows this order:

1. Specific validator messages (by public key)
2. Ordered messages (in sequence)
3. Random messages (chosen randomly)
4. Default fallback message from the file
5. CLI default message (from the `--validators-graffiti` parameter)

## Usage

To use multimode graffiti, create a YAML file with your desired configuration and specify it with the `--validators-graffiti-file` option:

```
teku --validators-graffiti-file=/path/to/graffiti.yaml
```

## Complete Example

Here's a complete example of a multimode graffiti configuration file:

```yaml
# Specific validator configuration by public key
specific:
  a057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a: "Validator 0xa05781 was here"
  b057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a: "Validator 0xb05781 was here"

# Ordered list of graffiti messages
ordered:
  - "First ordered graffiti message"
  - "Second ordered graffiti message"
  - "Third ordered graffiti message"
  - "Fourth ordered graffiti message"

# Random list of graffiti messages
random:
  - "Random message 1"
  - "Random message 2"
  - "Random message 3"
  - "Random message 4"
  - "Random message 5"

# Default fallback message
defaultGraffiti: "Default fallback graffiti message"
```

## Backwards Compatibility

For backward compatibility, you can still use a simple text file with a single graffiti message. Teku will detect the file format automatically and use the appropriate parser. 
