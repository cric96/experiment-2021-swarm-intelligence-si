import java.io.ByteArrayOutputStream

plugins {
    application
    alias(libs.plugins.gitSemVer)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.taskTree)
    alias(libs.plugins.scalafmt)
    alias(libs.plugins.kotlin.qa)
    alias(libs.plugins.multiJvmTesting)
    scala
}

repositories {
    mavenCentral()
}

val usesJvm: Int = File(File(projectDir, "util"), "Dockerfile")
    .readText()
    .let {
        Regex("FROM\\s+openjdk:(\\d+)\\s+$").find(it)?.groups?.get(1)?.value
            ?: throw IllegalStateException("Cannot read information on the JVM to use.")
    }
    .toInt()

multiJvm {
    jvmVersionForCompilation.set(usesJvm)
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.bundles.alchemist.bundle)
    implementation(libs.bundles.utils.bundle)
}

// Heap size estimation for batches
val maxHeap: Long? by project
val heap: Long = maxHeap ?: if (System.getProperty("os.name").toLowerCase().contains("linux")) {
    ByteArrayOutputStream().use { output ->
        exec {
            executable = "bash"
            args = listOf("-c", "cat /proc/meminfo | grep MemAvailable | grep -o '[0-9]*'")
            standardOutput = output
        }
        output.toString().trim().toLong() / 1024
    }.also { println("Detected ${it}MB RAM available.") } * 9 / 10
} else {
    // Guess 16GB RAM of which 2 used by the OS
    14 * 1024L
}
val standardEffectFile = "standard"
val taskSizeFromProject: Int? by project
val taskSize = taskSizeFromProject ?: 512
val threadCount = maxOf(1, minOf(Runtime.getRuntime().availableProcessors(), heap.toInt() / taskSize))

val alchemistGroup = "Run Alchemist"
/*
 * This task is used to run all experiments in sequence
 */
val runAllGraphic by tasks.register<DefaultTask>("runAllGraphic") {
    group = alchemistGroup
    description = "Launches all simulations with the graphic subsystem enabled"
}

val runAllBatch by tasks.register<DefaultTask>("runAllBatch") {
    group = alchemistGroup
    description = "Launches all experiments"
}

val runAllTest by tasks.register<DefaultTask>("runAllTest") {
    group = alchemistGroup
    description = "Launches all tests (in graphic subsystem)"
}

val runAllBatchUsingSeed by tasks.register<DefaultTask>("runAllBatchUsingSeed") {
    group = alchemistGroup
    description = "Launches all experiments (varying only the seed"
}
/*
 * Scan the folder with the simulation files, and create a task for each one of them.
 */
File(rootProject.rootDir.path + "/src/main/yaml").listFiles()
    ?.filter { it.extension == "yml" }
    ?.sortedBy { it.nameWithoutExtension }
    ?.forEach {
        fun basetask(name: String, additionalConfiguration: JavaExec.() -> Unit = {}) = tasks.register<JavaExec>(name) {
            group = alchemistGroup
            description = "Launches graphic simulation ${it.nameWithoutExtension}"
            main = "it.unibo.alchemist.Alchemist"
            classpath = sourceSets["main"].runtimeClasspath
            args("-y", it.absolutePath)
            if (System.getenv("CI") == "true") {
                args("-hl", "-t", "2")
            } else {
                val simulationFile = File(rootProject.rootDir.path + "/effects/${it.nameWithoutExtension}.aes")
                if (simulationFile.exists()) {
                    args("-g", "effects/${it.nameWithoutExtension}.aes")
                } else {
                    args("-g", "effects/$standardEffectFile.aes")
                }
            }
            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(usesJvm))
                }
            )
            this.additionalConfiguration()
        }
        fun batchWithArgs(postFix: String, vararg args: String): JavaExec {
            val capitalizedName = it.nameWithoutExtension.capitalize()
            val batch by basetask("run${capitalizedName}Batch${postFix}") {
                description = "Launches batch experiments for $capitalizedName"
                jvmArgs("-XX:+AggressiveHeap")
                maxHeapSize = "${minOf(heap.toInt(), Runtime.getRuntime().availableProcessors() * taskSize)}m"
                File("data").mkdirs()
                args(
                    "-e", "data/${it.nameWithoutExtension}",
                    "-b",
                    "-var", "seed", *args,
                    "-p", threadCount,
                    "-i", 1
                )
            }
            return batch
        }
        val capitalizedName = it.nameWithoutExtension.capitalize()
        val graphic by basetask("run${capitalizedName}Graphic")
        val batchAll = batchWithArgs(
            "AllVars",
            "in_cluster_thr",
            "same_cluster_thr",
            "candidate_in_hysteresis",
            "speed",
            "density",
            "sample"
        )
        val onlySeed = batchWithArgs("OnlySeed")
        if(!capitalizedName.contains("Test")) {
            runAllGraphic.dependsOn(graphic)
            runAllBatch.dependsOn(batchAll)
            runAllBatchUsingSeed.dependsOn(onlySeed)
        } else {
            runAllTest.dependsOn(graphic)
        }
    }
// add scalafmt dependencies
tasks.withType<ScalaCompile>() {
    dependsOn(tasks.named("scalafmtAll"))
}
