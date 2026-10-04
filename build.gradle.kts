plugins {
    java
}

group = "dev.the_fireplace.caterpillar"
// CI builds carry the short commit id (SimplyCaterpillar-0.2.0-ab12cd3.jar), so it is always clear which build is
// installed on a server.
val commit = System.getenv("GITHUB_SHA")?.take(7)
version = if (commit != null) "0.2.0-$commit" else "0.2.0-dev"

base {
    archivesName.set("SimplyCaterpillar")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}
