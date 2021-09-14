Demo:

The demo below provisions Wildfly Core feature pack.
It uses docker to host a nexus repository and requires
building galleo-plugins and wildfly-core projects

1. Build Wildfly Core
   1. Build prospero-galleon-plugin
      ```
         cd <PROSPERO_HOME>
         mvn clean install -Dgalleon
      ```
   1. Build galleon-plugins
      ```
         git clone https://github.com/spyrkob/galleon-plugins.git -b prospero <PATH_TO_GALLEON_PLUGINS>
         cd <PATH_TO_GALLEON_PLUGINS>
         mvn clean install -DskipTests
      ```
   1. Build wildfly-core with updated galleon-plugin
      ```
         git clone https://github.com/spyrkob/wildfly-core.git -b prospero <PATH_TO_WF_CORE>
         cd <PATH_TO_WF_CORE>
         mvn clean install -DskipTests
      ```
1. Setup nexus with channel repository   
   1. Start and setup nexus container in docker (or other MVN repository)
      ```
         cd <PROSPERO_HOME>
         ./scripts/setupNexus.sh
      ```
   1. Add nexus credential in `~/.m2/settings.xml` 
       ```
      <servers>
      ...
          <server>
              <id>nexus2</id>
              <username>admin</username>
              <password>admin</password>
          </server>
      ...
      </servers>
      ```

   1. Deploy artifacts into repositories
      ```
         WILDFLY_CORE_SRC="<PATH_TO_WF_CORE>" ./scripts/upload-artifacts.sh
         WILDFLY_CORE_SRC="<PATH_TO_WF_CORE>" \
           GALLEON_SRC=<PATH_TO_GALLEON_PLUGINS> \
           ./scripts/upload-galleon-.sh
      ```
1. Build prospero-cli
   ```
      mvn clean install -pl prospero-cli
   ```
1. Provision server
   ```
      ./prospero install wildfly-core:current/snapshot eap-dev dev-channels.json
   ```
