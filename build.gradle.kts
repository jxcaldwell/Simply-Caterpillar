import java.security.MessageDigest

plugins {
    java
}

group = "dev.the_fireplace.caterpillar"
// CI builds carry the short commit id (SimplyCaterpillar-0.5.0-ab12cd3.jar), so it is always clear which build is
// installed on a server.
val commit = System.getenv("GITHUB_SHA")?.take(7)
version = if (commit != null) "0.5.0-$commit" else "0.5.0-dev"

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

// ---- resource pack (milestone 5)
// The pack is zipped from resourcepack/; its SHA-1 and download link are written into the jar so the plugin can offer
// exactly this pack to players. CI publishes the zip as a GitHub release asset under the tag "build-<commit>".
val packZip = tasks.register<Zip>("resourcePackZip") {
    from("resourcepack")
    archiveFileName.set("SimplyCaterpillar-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("pack"))
}

val packInfoDir = layout.buildDirectory.dir("generated/pack-info")
val packInfo = tasks.register("resourcePackInfo") {
    dependsOn(packZip)
    val zipFile = packZip.flatMap { it.archiveFile }
    val repo = System.getenv("GITHUB_REPOSITORY") ?: ""
    val tagCommit = commit ?: ""
    inputs.file(zipFile)
    inputs.property("repo", repo)
    inputs.property("commit", tagCommit)
    outputs.dir(packInfoDir)
    doLast {
        val bytes = zipFile.get().asFile.readBytes()
        val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val url = if (repo.isNotEmpty() && tagCommit.isNotEmpty())
            "https://github.com/$repo/releases/download/build-$tagCommit/SimplyCaterpillar-pack.zip" else ""
        val out = packInfoDir.get().file("resourcepack.properties").asFile
        out.parentFile.mkdirs()
        out.writeText("url=$url\nsha1=$sha1\n")
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
    dependsOn(packInfo)
    from(packInfoDir)
}

tasks.test {
    useJUnitPlatform()
}
