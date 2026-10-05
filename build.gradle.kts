import java.security.MessageDigest

plugins {
    java
}

group = "dev.the_fireplace.caterpillar"
val baseVersion = "1.0.0"
// A release is built from a tag such as v1.0.0 and carries exactly that version. Every other CI build carries the short
// commit id (SimplyCaterpillar-1.0.0-ab12cd3.jar), so it is always clear which build is installed on a server.
val commit = System.getenv("GITHUB_SHA")?.take(7)
val releaseTag = System.getenv("GITHUB_REF_NAME")
    ?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" && it.startsWith("v") }
version = when {
    releaseTag != null -> releaseTag.removePrefix("v")
    commit != null -> "$baseVersion-$commit"
    else -> "$baseVersion-dev"
}

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
// exactly this pack to players. CI publishes the zip as a GitHub release asset: under the release tag (v1.0.0) for
// releases, which are permanent, and under "build-<commit>" for the automatic test builds, which are pruned.
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
    val tag = releaseTag ?: commit?.let { "build-$it" } ?: ""
    inputs.file(zipFile)
    inputs.property("repo", repo)
    inputs.property("tag", tag)
    outputs.dir(packInfoDir)
    doLast {
        val bytes = zipFile.get().asFile.readBytes()
        val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val url = if (repo.isNotEmpty() && tag.isNotEmpty())
            "https://github.com/$repo/releases/download/$tag/SimplyCaterpillar-pack.zip" else ""
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
