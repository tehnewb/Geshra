# Publishing Geshra to Maven Central

The project publishes a Java library with sources, generated Javadoc, MIT license metadata, developer information, and source repository information. Maven and Gradle resolve its dependencies from the published POM and Gradle module metadata.

## Set up the publisher account

1. Create a [Central Portal account](https://central.sonatype.org/register/central-portal/) and [verify the namespace](https://central.sonatype.org/register/namespace/) used by the library's group ID.
2. Generate a [Central Portal user token](https://central.sonatype.org/publish/generate-portal-token/).
3. Create a GPG signing key and [publish its public key](https://central.sonatype.org/publish/requirements/gpg/).

The existing group ID is preserved as `io.github.albertbeaupre`. The source repository's current owner is `tehnewb`; the repository URL alone does not establish ownership of the existing group. Verify the namespace you actually control before releasing. To use `io.github.tehnewb`, pass `-PlibraryGroup=io.github.tehnewb` and update the dependency examples to those coordinates. Once released, consumers depend on the chosen group ID.

## Supply credentials and signing

Keep credentials in your user Gradle configuration or environment. Do not put them in the repository. The [publishing plugin documentation](https://vanniktech.github.io/gradle-maven-publish-plugin/central/) describes the supported signing configuration.

For environment configuration, supply:

```text
ORG_GRADLE_PROJECT_mavenCentralUsername=<Central token username>
ORG_GRADLE_PROJECT_mavenCentralPassword=<Central token password>
ORG_GRADLE_PROJECT_signingInMemoryKey=<complete ASCII armored private key>
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=<key passphrase>
```

For a key without a passphrase, omit `signingInMemoryKeyPassword`. Optionally supply `ORG_GRADLE_PROJECT_signingInMemoryKeyId` when using a signing subkey.

## Build and stage a release

Choose a release version that has never been published. The following command prepares and uploads `1.0.0`:

```sh
./gradlew clean build publishToMavenCentral "-PreleaseVersion=1.0.0" -PreleaseSigning=true
```

Append `-PlibraryGroup=<verified group>` if needed. On Windows, use `.\gradlew.bat`.

The default checkout version is `1.0.0-SNAPSHOT`. Always set `releaseVersion` for a public release. Central publishing requires `releaseSigning=true`; local builds and local publication work without signing. Central validates the signed library, sources, Javadoc, POM, and accompanying metadata against its [publishing requirements](https://central.sonatype.org/publish/requirements/).

Open the deployment in the [Central Portal](https://central.sonatype.com/publishing/deployments), review validation, and select Publish. Consumers can then use `mavenCentral()` or Maven's default Central repository. The preparation in this checkout does not itself publish a public release.

## Publish through GitHub Actions

Configure these repository secrets:

```text
MAVEN_CENTRAL_USERNAME
MAVEN_CENTRAL_PASSWORD
SIGNING_KEY
SIGNING_PASSWORD
```

Run the **Stage Maven Central release** workflow manually with the release version and verified group ID. It tests the library, signs the publication, and uploads it for manual publication in the Central Portal.
