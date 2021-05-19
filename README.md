Demo:

1. Build Wildfly from https://github.com/wildfly/wildfly in ${WILDFLY_SOURCE}
1. Build prospero `mvn clean install`
1. Prepare a prospero repository `./demo init ${WILDFLY_SOURCE}/build/target/wildfly-24.0.0.Beta1-SNAPSHOT`.
    This will create a folder target/prospero-repo.
1. Provision new installation `./prospero install`.
    This will create a new Wildfly installation in target/wfly.
1. Mock up updated artifacts:
    1. new version of undertow `./demo mock undertow-core 2.2.7.Final`
    1. new version of wildfly-undertow with dependency on undertow `./demo mock wildfly-undertow 24.0.1.Final io.undertow:undertow-core:2.2.8.Final`
1. Update wildfly-core `./prospero update org.wildfly:wildfly-undertow`
