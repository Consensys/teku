# Running Artemis

You can build and run Artemis with default options via:

```
./gradlew run
```

By default this stores all persistent data in `build/artemis`.

If you want to set custom CLI arguments for the Artemis execution, you can use the property `artemis.run.args` like e.g.:

```sh
./gradlew run -Partemis.run.args="--discovery=false --home=/tmp/artemistmp"
```

which will pass `--discovery=false` and `--home=/tmp/artemistmp` to the invocation.