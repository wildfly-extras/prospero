## Prospero 
Prospero is a tool combining Galleon feature packs and wildfly-channels to provision 
and update Wildfly server.

## Example:
The demo below provisions and updates Wildfly 27.0.0.Alpha1.

1. Build prospero
   ```
      cd <PROSPERO_HOME>
      mvn clean install
   ```
2. Provision server
   ```
      ./<PROSPERO_HOME>/prospero install --fpl=wildfly --dir=wfly-27 --channel=examples/wildfly-27.0.0.Alpha1-channel.yaml
   ```
3. Update server
   1. Edit `examples/wildfly-27.0.0.Alpha1-channel.yaml` and update undertow-core version to:
   ```
      - groupId: "io.undertow"
        artifactId: "undertow-core"
        version: "2.2.17.Final"
   ```
   2. Update server
   ```
      ./<PROSPERO_HOME>/prospero update --dir=wfly-27
   ```
