# LegacyLauncher - mikroskeem's fork

This LegacyLauncher fork is used with [Orion](https://github.com/OrionMinecraft/Orion), introducing code cleanup,
bugfixes and experimental Java 9 support, whereas retaining API-compatibility with other tools.

## Buidling LegacyLauncher
Invoke `./gradlew build`

## Getting LegacyLauncher
```kotlin
val legacyLauncherVersion: String by extra

repositories {
    maven("https://repo.wut.ee/repository/mikroskeem-repo")
}

dependencies {
    implementation("eu.mikroskeem:legacylauncher:$legacylauncherVersion")
}
```