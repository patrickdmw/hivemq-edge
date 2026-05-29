import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED



plugins {
    java
    alias(libs.plugins.defaults)
    alias(libs.plugins.shadow)
    alias(libs.plugins.hivemq.license)
    id("com.hivemq.edge-version-updater")
    id("com.hivemq.repository-convention")
    id("com.hivemq.jacoco-convention")
    id("com.hivemq.errorprone-convention")
    id("com.hivemq.nullaway-convention")
    id("com.hivemq.spotless-convention")
}

group = "com.hivemq"

repositories {
    mavenLocal()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// exclude old transitive dependency versions that are provided by edge
configurations.runtimeClasspath {
    exclude("io.netty", "netty-buffer")
    exclude("io.netty", "netty-handler")
    exclude("io.netty", "netty-codec")
    exclude("io.netty", "netty-common")
    exclude("io.netty", "netty-transport")
}

// OSGi annotations are compile-time only (bundle metadata); not resolvable/needed here
configurations.all {
    exclude(group = "org.osgi")
}

dependencies {
    compileOnly(libs.hivemq.edge.adaptersdk)
    compileOnly(libs.apache.commons.io)

    implementation("org.apache.plc4x:plc4j-driver-s7:0.14.0-SNAPSHOT")
//    implementation(libs.plc4j.s7)
    implementation("org.apache.plc4x:plc4j-driver-ads:0.14.0-SNAPSHOT")
//     implementation(libs.plc4j.ads)
    implementation("org.apache.plc4x:plc4j-api:0.14.0-SNAPSHOT")
//     implementation(libs.plc4j.api)
    implementation("org.apache.plc4x:plc4j-transports-raw-socket:0.14.0-SNAPSHOT")
//     implementation(libs.plc4j.transport.raw.socket)

    implementation("com.fasterxml.jackson.core:jackson-annotations:2.21")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")

    constraints {
        implementation(libs.org.json)
    }
}

dependencies {
    testImplementation("com.hivemq:hivemq-edge")
    testImplementation(libs.hivemq.edge.adaptersdk)
    testImplementation("org.apache.plc4x:plc4j-api:0.14.0-SNAPSHOT")

    testImplementation(libs.apache.commons.io)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events = setOf(STARTED, PASSED, FAILED, SKIPPED, STANDARD_ERROR)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.register<Copy>("copyAllDependencies") {
    shouldRunAfter("assemble")
    from(provider { configurations.runtimeClasspath.get() })
    into(layout.buildDirectory.dir("deps/libs"))
}

tasks.named("assemble") { finalizedBy("copyAllDependencies") }

// ******************** artifacts ********************

val releaseBinary: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("binary"))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("release"))
    }
}

val thirdPartyLicenses: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("third-party-licenses"))
    }
}

artifacts {
    add(releaseBinary.name, tasks.shadowJar)
    add(
        thirdPartyLicenses.name,
        tasks.updateThirdPartyLicenses.flatMap { it.outputDirectory }
    )
}
// ******************** compliance ********************

hivemqLicense {
    projectName.set(project.name)
    thirdPartyLicenseDirectory.set(layout.projectDirectory.dir("src/distribution/third-party-licenses"))
}
