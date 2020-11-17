#!/usr/bin/env kotlin

@file:Repository("https://mvnrepository.com")
@file:DependsOn("com.fasterxml.jackson.core:jackson-databind:2.11.2")
@file:DependsOn("com.fasterxml.jackson.core:jackson-core:2.11.2")
@file:DependsOn("com.fasterxml.jackson.core:jackson-annotations:2.11.2")


import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.*
import java.util.concurrent.TimeUnit


data class Package(
    @JsonProperty("flags") val flags: List<String>,
    @JsonProperty("package") val app: String
)

// Includes package managers such as pacman/yay, etc
data class PackageManager(
    @JsonProperty("type") val type: String,
    @JsonProperty("sudo") val sudo: Boolean,
    @JsonProperty("packages") val packages: List<Package>
)

// Custom install scripts if you need to e.g build from source
data class Custom(
    @JsonProperty("name") val name: String,
    @JsonProperty("message") val message: String,
    @JsonProperty("path") val path: String
)

fun String.runAsProcess(workingDir: File = File(System.getProperty("user.dir"))) {
    ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(1, TimeUnit.HOURS)
}

// Outer object containing a list of package managers and custom scripts objects
data class Head(
    @JsonProperty("package_manager") val packageManager: List<PackageManager>?,
    @JsonProperty("custom") val custom: List<Custom>?
)

fun main() {
    // Reads the json data into a string
    val resources = BufferedReader(FileReader(File("packages.json"))).readLines().joinToString("\n")

    // Deserializes the json data
    val packageHead = ObjectMapper().readValue(resources, Head::class.java)


    if (packageHead.packageManager != null || packageHead.custom != null)
        println("Installing following packages:")

    // Package manager applications
    println(packageHead.packageManager?.flatMap { packages ->
        mutableListOf(*packages.packages.toTypedArray()).map { it.app }
    }?.joinToString("\n"))

    // Custom applications
    println(packageHead.custom?.joinToString("\n") { it.name })

    // Install the package managers' packages first
    packageHead.packageManager?.forEach {
        for (module in it.packages) {
            val command = "${if (it.sudo) "sudo " else ""}${it.type} ${module.flags.joinToString(" ")} ${module.app}"
            command.runAsProcess()
        }
    }

    // Then runs the custom scripts
    packageHead.custom?.forEach {
        // TODO: Check if URL or local file and runs the file
    }

    println("Done installing packages..")
}

main()