import groovy.util.Node
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    id("net.minecrell.licenser") version "0.3"
    `maven-publish`
}

group = "io.akarin"
version = "2.1"
description = "Minecraft LegacyLauncher - Akarin project"

val joptSimpleVersion = "5.0.4"
val asmVersion = "5.2"
val slf4jVersion = "1.8.0-beta2"
val jbAnnotationsVersion = "24.1.0"

val gradleWrapperVersion = "4.6"

val lwtsVersion = "1.1.0-SNAPSHOT"
val shurikenVersion = "0.0.1-SNAPSHOT"

val mixinVersion = "0.8.7-SNAPSHOT"
val paperApiVersion = "1.20.4-R0.1-SNAPSHOT"

repositories {
    maven("https://repo.papermc.io/repository/maven-releases/")
    maven("https://repo.papermc.io/repository/maven-snapshots/")
    // maven("https://repo.wut.ee/repository/mikroskeem-repo") // DEAD
    maven("https://repo.spongepowered.org/repository/maven-public/")
    mavenCentral()
    mavenLocal()
}

dependencies {
    compile("net.sf.jopt-simple:jopt-simple:$joptSimpleVersion")
    compile("org.ow2.asm:asm:$asmVersion")
    compile("org.slf4j:slf4j-api:$slf4jVersion")
    compile("org.jetbrains:annotations:$jbAnnotationsVersion")
    compile("org.spongepowered:mixin:$mixinVersion")
	compile("io.papermc.paper:paper-api:$paperApiVersion")

    testImplementation("org.spongepowered:lwts:$lwtsVersion") {
        exclude(group = "net.minecraft", module = "launchwrapper")
    }
    // testImplementation("eu.mikroskeem:shuriken.instrumentation:$shurikenVersion") // DEAD
    // testImplementation("org.slf4j:slf4j-api:$slf4jVersion")
}

val test by tasks.getting(Test::class) {
    systemProperty("lwts.tweaker", "eu.mikroskeem.test.launchwrapper.tweaker.TestTweaker")
    systemProperty("legacy.debugClassLoading", "true")
    systemProperty("legacy.debugClassLoadingFiner", "true")
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace")

    // Set working directory
    workingDir = this.temporaryDir

    // Show output
    testLogging {
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }

    // Verbose
    beforeTest(closureOf<Any> { logger.lifecycle("Running test: $this") })
}

license {
    header = rootProject.file("etc/HEADER")
    filter.include("**/*.java")

    // Because org.spongepowered.asm.launch.MixinBootstrap does this:
    // if(MixinBootstrap.findInStackTrace("net.minecraft.launchwrapper.Launch", "launch") > 132) {
    //     *we are not in pre-init some mixins may be skipped blah blah*
    filter.exclude("net/minecraft/launchwrapper/Launch.java")
}

val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(java.sourceSets["main"].allSource)
}

val javadoc by tasks.getting(Javadoc::class)

val javadocJar by tasks.creating(Jar::class) {
    dependsOn(javadoc)
    classifier = "javadoc"
    from(javadoc.destinationDir)
}

val wrapper by tasks.creating(Wrapper::class) {
    gradleVersion = gradleWrapperVersion
    distributionUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
}

publishing {
    (publications) {
        "maven"(MavenPublication::class) {
            artifactId = "legacylauncher"

            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)

            pom.withXml {
                builder {
                    "name"("LegacyLauncher")
                    "description"(project.description)
                    "url"("https://github.com/Akarin-project/LegacyLauncher")

                    "issueManagement" {
                        "system"("GitHub Issues")
                        "url"("https://github.com/Akarin-project/LegacyLauncher/issues")
                    }

                    "licenses" {
                        "license" {
                            "name"("MIT License")
                            "url"("https://opensource.org/licenses/MIT")
                        }
                    }

                    "developers" {
                        "developer" {
                            "id"("mikroskeem")
                            "name"("Mark Vainomaa")
                            "email"("mikroskeem@mikroskeem.eu")
                        }
                    }

                    "scm" {
                        "connection"("scm:git@github.com:Akarin-project/LegacyLauncher.git")
                        "developerConnection"("scm:git@github.com:Akarin-project/LegacyLauncher.git")
                        "url"("https://github.com/Akarin-project/LegacyLauncher")
                    }
                }
            }
        }
    }

    repositories {
        mavenLocal()

        maven {
            name = "Akarin-repo"
            setUrl("file:/" + rootProject.properties["repoParent"] + "Akarin-repo/repository/")
        }
    }
}

fun XmlProvider.builder(builder: GroovyBuilderScope.() -> Unit) {
    (asNode().children().last() as Node).plus(delegateClosureOf<Any> {
        withGroovyBuilder(builder)
    })
}
