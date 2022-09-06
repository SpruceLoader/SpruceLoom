import groovy.lang.MissingPropertyException
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.util.GradleVersion
import java.net.URL

plugins {
    java
    `maven-publish`
    `java-gradle-plugin`
    idea
    eclipse
    groovy
    checkstyle
    jacoco
    codenarc
    kotlin("jvm") version ("1.6.10") // Must match the version included with gradle.
    id("com.diffplug.spotless") version ("6.8.0")
}

group = extra["project.group"]?.toString() ?: throw MissingPropertyException("Project group is missing!")
version = extra["project.version"]?.toString() ?: throw MissingPropertyException("Project version is missing!")

// Apply additional things which aren't possible in the Kotlin DSL...
apply(from = "gradle/groovy.gradle")

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

val bootstrap by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations.testRuntimeClasspath.get().extendsFrom(this)
}

configurations.all {
    resolutionStrategy {
        failOnNonReproducibleResolution()
    }
}

dependencies {
    implementation(gradleApi())

    bootstrap(project(":bootstrap"))

    // libraries
    implementation("commons-io:commons-io:2.11.0")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.ow2.asm:asm:9.3")
    implementation("org.ow2.asm:asm-analysis:9.3")
    implementation("org.ow2.asm:asm-commons:9.3")
    implementation("org.ow2.asm:asm-tree:9.3")
    implementation("org.ow2.asm:asm-util:9.3")
    implementation("com.github.mizosoft.methanol:methanol:1.7.0")

    // game handling utils
    implementation("net.fabricmc:stitch:0.6.2") {
        exclude(module = "enigma")
    }

    // tinyfile management
    implementation("net.fabricmc:tiny-remapper:0.8.5")
    implementation("net.fabricmc:access-widener:2.1.0")
    implementation("net.fabricmc:mapping-io:0.2.1")

    implementation("net.fabricmc:lorenz-tiny:4.0.2") {
        isTransitive = false
    }

    // decompilers
    implementation("net.fabricmc:fabric-fernflower:1.5.0")
    implementation("net.fabricmc:cfr:0.1.1")

    // source code remapping
    implementation("net.fabricmc:mercury:0.2.6")

    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.4.2") {
        isTransitive = false
    }

    // Kapt integration
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10") // Must match the version included with gradle.

    // Testing
    testImplementation(gradleTestKit())
    testImplementation("org.spockframework:spock-core:2.1-groovy-3.0") {
        exclude(module = "groovy-all")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    testImplementation("io.javalin:javalin:4.6.1") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testImplementation("net.fabricmc:fabric-installer:0.9.0")
    testImplementation("org.mockito:mockito-core:4.6.1")

    compileOnly("org.jetbrains:annotations:23.0.0")
    testCompileOnly("org.jetbrains:annotations:23.0.0")

    testCompileOnly("net.fabricmc:sponge-mixin:0.11.4+mixin.0.8.5") {
        isTransitive = false
    }
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    listOf(
        "Releases",
        "Snapshots"
    ).forEach { branch ->
        register("publishPluginTo$branch") {
            group = "uniloom"
            dependsOn("publishUniloomPluginMarkerMavenPublicationToUnifyCraft${branch}Repository")
            dependsOn("publishPluginMavenPublicationToUnifyCraft${branch}Repository")
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        manifest.attributes(
            "Implementation-Version" to project.version
        )

        from(bootstrap.map {
            if (it.isDirectory) it else zipTree(it)
        })
    }

    wrapper {
        distributionType = Wrapper.DistributionType.ALL
    }

    jacoco {
        toolVersion = "0.8.7"
    }

    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required.set(false)
            csv.required.set(false)
            html.outputLocation.set(file("${buildDir}/jacocoHtml"))
        }
    }

    test {
        maxHeapSize = "2560m"
        useJUnitPlatform()

        // Forward system prop onto tests.
        val systemProp = System.getProperty("fabric.loom.test.homeDir")
        if (systemProp != null) systemProperty("fabric.loom.test.homeDir", systemProp)
    }

    register("writeActionsTestMatrix") {
        doLast {
            val testMatrix = mutableListOf<String>()
            file("src/test/groovy/net/fabricmc/loom/test/integration").listFiles()?.forEach {
                if (it.name.endsWith("Test.groovy")) {
                    if (it.name.endsWith("ReproducibleBuildTest.groovy")) {
                        // This test gets a special case to run across all OS's
                        return@forEach
                    }

                    val className = it.name.replace(".groovy", "")

                    // Disabled for CI, as it fails too much.
                    if (className.endsWith("DecompileTest")) return@forEach

                    testMatrix.add("net.fabricmc.loom.test.integration.${className}")
                }
            }

            // Run all the unit tests together
            testMatrix.add("net.fabricmc.loom.test.unit.*")

            // Kotlin tests
            testMatrix.add("net.fabricmc.loom.test.kotlin.*")

            val json = groovy.json.JsonOutput.toJson(testMatrix)
            val output = file("build/test_matrix.json")
            output.parentFile.mkdir()
            output.writeText(json)
        }
    }

    register("downloadGradleSources") {
        doLast {
            // Awful hack to find the gradle API location
            val gradleApiFile = project.configurations.detachedConfiguration(project.dependencies.gradleApi()).files.stream()
                .filter {
                    it.name.startsWith("gradle-api")
                }.findFirst().orElseThrow { IllegalStateException("Could not find Gradle API.") }
            val gradleApiSources = File(gradleApiFile.absolutePath.replace(".jar", "-sources.jar"))
            val url = "https://services.gradle.org/distributions/gradle-${GradleVersion.current().getVersion()}-src.zip"

            gradleApiSources.delete()

            logger.lifecycle("Downloading (${url}) to (${gradleApiSources})")
            URL(url).openStream().use { input ->
                gradleApiSources.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}

spotless {
    java {
        licenseHeaderFile(rootProject.file("HEADER")).yearSeparator("-")
        targetExclude("**/loom/util/DownloadUtil.java")
    }

    groovy {
        licenseHeaderFile(rootProject.file("HEADER")).yearSeparator("-")
    }

    kotlin {
        licenseHeaderFile(rootProject.file("HEADER")).yearSeparator("-")
        targetExclude("**/build.gradle.kts")
        targetExclude("src/test/resources/projects/*/**")
        ktlint()
    }
}

checkstyle {
    configFile = file("checkstyle.xml")
    toolVersion = "10.3.1"
}

codenarc {
    toolVersion = "3.1.0"
    configFile = file("codenarc.groovy")
}

gradlePlugin {
    plugins {
        create("uniloom") {
            id = "xyz.unifycraft.uniloom"
            implementationClass = "net.fabricmc.loom.bootstrap.LoomGradlePluginBootstrap"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            groupId = project.group.toString()
            artifactId = extra["project.name"]?.toString()?.toLowerCase() ?: throw MissingPropertyException("Project name is missing!")
            version = project.version.toString()

            from(components["java"])
        }
    }

    repositories {
        if (project.hasProperty("unifycraft.publishing.username") && project.hasProperty("unifycraft.publishing.password")) {
            fun MavenArtifactRepository.applyCredentials() {
                credentials {
                    username = property("unifycraft.publishing.username")?.toString()
                    password = property("unifycraft.publishing.password")?.toString()
                }
                authentication.create<BasicAuthentication>("basic")
            }

            maven {
                name = "UnifyCraftRelease"
                url = uri("https://maven.unifycraft.xyz/releases")
                applyCredentials()
            }

            maven {
                name = "UnifyCraftSnapshots"
                url = uri("https://maven.unifycraft.xyz/snapshots")
                applyCredentials()
            }
        }
    }
}
