plugins {
	java
	groovy
}

repositories {
	mavenCentral()
}

dependencies {
	compileOnly(gradleApi())

	testImplementation(gradleTestKit())
	testImplementation("org.spockframework:spock-core:2.1-groovy-3.0") {
		exclude(module = "groovy-all")
	}
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(17))
	}
}

tasks {
	withType<JavaCompile> {
		options.encoding = "UTF-8"
	}

	test {
		maxHeapSize = "4096m"
		useJUnitPlatform()
	}
}
