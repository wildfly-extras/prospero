# Usage Examples

This file shows how to use the Prospero CLI tool to create an installation of the Wildfly application server. These instructions
are provided for development and demonstrational purposes only. In practice, no channels are expected to be published for
Wildfly (upstream project), and the Prospero tool would only be used to provision the JBoss EAP (donwstream product).

Prospero needs two required inputs, to be able to provision an installation:

* a Galleon *feature pack location*,
* a *Wildfly Channel* reference.

## Dictionary of Terms
<dl>
    <dt>Galleon Feature Pack</dt>
    <dd>
        The *Galleon feature pack* is a package containing all the metadata required to compose an installation of the Wildfly 
        or JBoss EAP application server. Among other things, the feature pack contains coordinates (GAVs) to Maven artifacts
        that the Wildfly server is composed of.
    </dd>
    <dt>Wildfly Channel</dt>
    <dd>
        The *Wildfly Channel* is another bit of metadata. It's composed of two things: a reference to a
        *Wildfly Channel manifest*, and a list of Maven repositories where above Maven artifacts can be found.
    </dd>
    <dt>Wildfly Channel Manifest</dt>
    <dd>
        The *Wildfly Channel Manifest* specifies versions of all the Maven artifacts that given feature
        pack depends on (these version override the original Maven artifacts versions defined in the feature pack).
    </dd>
</dl>

### Wildfly Channels & Manifests

Typically, the Wildfly Channels and Wildfly Channel Manifests would be distributed as well in a form of Maven artifacts and they
would be referenced via Maven coordinates (GAVs). This directory contains sample channel and manifest files to use when no
channels / manifests have been officially published yet:

* [wildfly-core-channel.yaml](wildfly-core-channel.yaml),
* [wildfly-core-manifest.yaml](wildfly-core-manifest.yaml).

(Note: These files have been generated for Wildfly Core version 20.0.0.Beta2.)

These files can be used to provision a Wildfly Core installation in following ways:

## Pre-requisite: Build Prospero

```shell
cd $PROSPERO_SOURCE_ROOT/
mvn clean package
```

## Installation of a Pre-Defined Product Version

```shell
./prospero install --fpl wildfly \
  --manifest examples/wildfly-27.0.0.Alpha2-manifest.yaml \
  --dir installation-dir
```

(Note: the `--manifest ...` option would be optional for predefined "eap-*" options.)

## Installation Referencing a Channel

```shell
./prospero install --fpl org.wildfly.core:wildfly-core-galleon-pack:20.0.0.Beta3 \
  --channels examples/wildfly-core-channel.yaml \
  --dir installation-dir
```

Description:
 * The `--fpl` option defines a feature pack to be installed.
 * The `--channels` option defines a Wildfly Channel (a *Wildfly Channel* references a *Wildfly Channel Manifest* and
Maven repositories). 

## Installation Referencing a Channel Manifest and Maven Repositories

```shell
./prospero install --fpl org.wildfly.core:wildfly-core-galleon-pack:20.0.0.Beta3 \
  --manifest examples/wildfly-core-manifest.yaml \
  --repositories central::https://repo1.maven.org/maven2/ \
  --dir installation-dir
```

Description:
* The `--fpl` option defines a feature pack to be installed.
* The `--manifest` option defines a Wildfly Channel Manifest (specifies Maven artifacts versions).
* The `--repositories` options defines Maven repositories to download Maven artifacts from.
