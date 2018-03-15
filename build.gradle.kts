import groovy.util.Node
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    id("net.minecrell.licenser") version "0.3"
    `maven-publish`
}

group = "eu.mikroskeem"
version = "1.18-SNAPSHOT"
description = "Minecraft LegacyLauncher - mikroskeem's fork"

val joptSimpleVersion = "5.0.4"
val asmVersion = "5.2"
val log4j2Version = "2.8.1"
val jbAnnotationsVersion = "15.0"

val gradleWrapperVersion = "4.6"

repositories {
    mavenLocal()
    mavenCentral()

    maven("https://repo.wut.ee/repository/mikroskeem-repo")
    maven("https://repo.spongepowered.org/maven")
}

dependencies {
    compile("net.sf.jopt-simple:jopt-simple:$joptSimpleVersion")
    compile("org.ow2.asm:asm-all:$asmVersion")
    compile("org.apache.logging.log4j:log4j-api:$log4j2Version")
    compile("org.jetbrains:annotations:$jbAnnotationsVersion")

    testImplementation("org.spongepowered:lwts:1.1.0-SNAPSHOT") {
        exclude(group = "net.minecraft", module = "launchwrapper")
    }
    testImplementation("org.apache.logging.log4j:log4j-core:$log4j2Version")
}

val test by tasks.getting(Test::class) {
    systemProperty("lwts.tweaker", "eu.mikroskeem.test.launchwrapper.TestTweaker")
    systemProperty("legacy.debugClassLoading", "true")
    systemProperty("legacy.debugClassLoadingFiner", "true")

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
                    "url"("https://github.com/OrionMinecraft/LegacyLauncher")

                    "issueManagement" {
                        "system"("GitHub Issues")
                        "url"("https://github.com/OrionMinecraft/LegacyLauncher/issues")
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
                        "connection"("scm:git@github.com:OrionMinecraft/LegacyLauncher.git")
                        "developerConnection"("scm:git@github.com:OrionMinecraft/LegacyLauncher.git")
                        "url"("https://github.com/OrionMinecraft/LegacyLauncher")
                    }
                }
            }
        }
    }

    repositories {
        mavenLocal()

        if(rootProject.hasProperty("wutRepoUsername") && rootProject.hasProperty("wutRepoPassword")) {
            maven {
                credentials {
                    username = rootProject.properties["wutRepoUsername"] as String
                    password = rootProject.properties["wutRepoPassword"] as String
                }

                name = "mikroskeem-repo"
                setUrl("https://repo.wut.ee/repository/mikroskeem-repo")
            }
        }
    }
}

fun XmlProvider.builder(builder: GroovyBuilderScope.() -> Unit) {
    (asNode().children().last() as Node).plus(delegateClosureOf<Any> {
        withGroovyBuilder(builder)
    })
}