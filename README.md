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
