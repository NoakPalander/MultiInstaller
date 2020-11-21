#!/usr/bin/env kotlin

@file:Repository("https://mvnrepository.com")
@file:DependsOn("com.fasterxml.jackson.core:jackson-databind:2.11.2")
@file:DependsOn("com.fasterxml.jackson.core:jackson-core:2.11.2")
@file:DependsOn("com.fasterxml.jackson.core:jackson-annotations:2.11.2")


import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess


fun String.runAsProcess(workingDir: File = File(System.getProperty("user.dir"))) {
    ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(1, TimeUnit.HOURS)
}

data class Package(
    @JsonProperty("flags") val flags: List<String>,
    @JsonProperty("package") val app: String
)

data class PackageManager(
    @JsonProperty("type") val type: String,
    @JsonProperty("sudo") val sudo: Boolean,
    @JsonProperty("packages") val packages: List<Package>
)

data class LocalCustom(
    @JsonProperty("prioritize") val prioritize: Boolean,
    @JsonProperty("name") val name: String,
    @JsonProperty("message") val message: String,
    @JsonProperty("path") val path: String)
{
    operator fun invoke() {
        println("LOCAL!")
    }
}

data class WebCustom(
    @JsonProperty("prioritize") val prioritize: Boolean,
    @JsonProperty("url") val urlString: String,
    @JsonProperty("message") val message: String
)

data class Custom(
    @JsonProperty("local") val local: LocalCustom?,
    @JsonProperty("web") val web: WebCustom?
)

data class Head(
    @JsonProperty("package_manager") val packageManager: List<PackageManager>?,
    @JsonProperty("custom") val custom: List<Custom>?
)

// Deserializes the resource into a Head object
fun deserialize(packageFile: String): Head {
    return try {
        val raw = URL(packageFile).readText()
        ObjectMapper().readValue(raw, Head::class.java)
    }
    // Invalid URL format treat as file
    catch(_: MalformedURLException) {
        val raw = File(packageFile).readText()
        ObjectMapper().readValue(raw, Head::class.java)
    }
}

fun runWebScript(urlString: String) {
    // Create temporary directory
    try {
        val temp = File(System.getProperty("user.dir"), ".temp")
        if (temp.exists())
            temp.delete()

        temp.mkdir()
        File("${temp.path}/web_script").writeText(URL(urlString).readText()) // TODO: error, getting newlines combined with '\r'
        println(URL(urlString).readText())
        "chmod +x web_script".runAsProcess(temp)
        "./.temp/web_script".runAsProcess()
    }
    catch(e: Exception) {
        println(e)
    }
}

fun runLocalScript() {

}

fun main() {
    // Gets the head object from the start argument
    // TODO: Parse the start arguments better
    val head = deserialize(args[0])

    if (head.packageManager != null || head.custom != null)
        println("Installing following packages:")

    // Prints the applications from the package manager list, if there is any
    head.packageManager?.forEach { manager ->
        manager.packages.forEach {
            println(it.app)
        }
    }

    // Prints the applications from the custom list, if there is any
    head.custom?.forEach {
        when {
            it.local != null -> println(it.local.name)
            it.web != null -> println(it.web.urlString)
        }
    }

    println("\nProceed with the installation? [y\\n]")
    print(">> ").also { System.out.flush() }
    if (readLine().toString().toLowerCase()[0] == 'n') {
        println("\nTerminating installation..")
        exitProcess(0)
    }

    // Install the package managers' packages first
    head.packageManager?.forEach {
        for (module in it.packages) {
            val command = "${if (it.sudo) "sudo " else ""}${it.type} ${module.flags.joinToString(" ")} ${module.app}"
            command.runAsProcess()
        }
    }

    // Then runs the custom scripts
    head.custom?.forEach { type ->
        when {
            type.local != null -> runLocalScript()
            type.web != null ->  runWebScript(type.web.urlString)
            else -> throw RuntimeException("Invalid custom build type. Either specify 'local' or 'web'!")
        }
        // TODO: Check if URL or local file and runs the file
    }

    println("Done installing packages..")
}

main()