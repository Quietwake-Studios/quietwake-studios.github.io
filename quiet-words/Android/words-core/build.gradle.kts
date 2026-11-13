plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

// The iOS package's content is the only source (gradle.properties: quietWords.iosResources).
val iosResources = rootProject.file(providers.gradleProperty("quietWords.iosResources").get())

// Java resources can't list a folder, so the theme files get an index at build time.
val themeIndex = tasks.register("themeIndex") {
    val themes = File(iosResources, "Themes")
    val out = layout.buildDirectory.dir("generated/themeIndex")
    inputs.dir(themes)
    outputs.dir(out)
    doLast {
        val names = themes.listFiles { f -> f.extension == "json" }!!.map { it.name }.sorted()
        val file = out.get().file("Themes/index.txt").asFile
        file.parentFile.mkdirs()
        file.writeText(names.joinToString("\n", postfix = "\n"))
    }
}

sourceSets.main {
    resources.srcDir(iosResources)
    resources.srcDir(themeIndex)
}

dependencies {
    api(project(":quietkit-core"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("quiet.fixtures", rootProject.file("fixtures").absolutePath)
    maxHeapSize = "1g"
    testLogging { events("failed"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
