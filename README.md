## Prospero 
Prospero is a tool combining Galleon feature packs and wildfly-channels to provision 
and update Wildfly server.

## Example:
The demo below provisions and updates Wildfly 27.0.0.Alpha2.

1. Build prospero
   ```
      cd <PROSPERO_HOME>
      mvn clean install
   ```
2. Provision server
   ```
      ./prospero install --profile=wildfly --dir=wfly-27 --channel=examples/wildfly-27.0.0.Alpha2-channel.yaml
   ```
3. Update server
   1. Edit `examples/wildfly-27.0.0.Alpha2-manifest.yaml` (configured in the wildfly-27.0.0.Alpha2-channel.yaml) and update undertow-core version to:
   ```
      - groupId: "io.undertow"
        artifactId: "undertow-core"
        version: "2.2.18.Final"
   ```
   2. Update server
   ```
      ./prospero update perform --dir=wfly-27
   ```

## Contributing
Please see the instructions available in the [contribution guide](CONTRIBUTING.md).

## Building distribution
The full distribution of Prospero includes a Galleon feature pack, a standalone zip and documentation. Building those projects is excluded by default and enabled only if a maven `dist` profile is enabled.
```
   cd <PROSPERO_HOME>
   mvn clean install -Pdist
```

## Running integration tests
Slower tests (e.g. including provisioning a full server), are located in integration-tests directory and are enabled by `-DallTests` property.
```
   cd <PROSPERO_HOME>
   mvn clean install -DallTests
```

License
-------
* [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)