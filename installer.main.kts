#!/usr/bin/env kotlin

@file:Repository("https://mvnrepository.com")
@file:DependsOn("com.fasterxml.jackson.core:jackson-databind:2.11.2")
@file:DependsOn("com.fasterxml.jackson.core:jackson-core:2.11.2")
@file:DependsOn("com.fasterxml.jackson.core:jackson-annotations:2.11.2")


import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.FileReader
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
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
    @JsonProperty("path") val path: String)

data class WebCustom(
    @JsonProperty("prioritize") val prioritize: Boolean,
    @JsonProperty("url") val url: String,
    @JsonProperty("name") val name: String
)

data class Custom(
    @JsonProperty("local") val local: LocalCustom? = null,
    @JsonProperty("web") val web: WebCustom? = null
)

data class Head(
    @JsonProperty("package_manager") val packageManager: List<PackageManager>? = null,
    @JsonProperty("custom") val custom: List<Custom>? = null
)

class Arguments {
    companion object {
        var targetManagers: List<String>? = null
        var targetOrder: List<String>? = null

        // Parses the start arguments and groups them into commands/args combos
        fun extract(args: Array<String>): List<Pair<String, List<String>?>> {
            val out = arrayListOf<Pair<String, List<String>?>>()
            for (arg: String in args) {
                if (arg.contains("=")) {
                    val split = arg.split("=")
                    out.add(Pair(split[0], split[1].split(",")))
                }
                else {
                    out.add(Pair(arg, null))
                }
            }

            return out
        }

        fun help(ignored: List<String>?) {
            println("\n" + """
                Usage: ./installer.main.kts [arguments..] [path to package].json
                Usage: ./installer.main.kts [arguments..] [url to raw package json file]
                Optional arguments:
                  --help -h                             Displays this message.
                  --target=[option(1)] -t=[option(1)]   Sets a specific target to build from the package file.
                  --order=[option(..),]                 Specifies which managers to build from in what order, ignores the order declared in the package file.
                  --install -i                          Installs the application to the linux system.
                  --uninstall -u                        Uninstalls the application from the linux system.
            """.trimIndent() + "\n\n")
            exitProcess(0)
        }

        fun target(args: List<String>?) {
            if (args == null)
                throw RuntimeException("Values for --target and or -t is required! Consult --help for more info.")

            targetManagers = args
        }

        fun order(args: List<String>?) {
            if (args == null)
                throw RuntimeException("Values for --order -o is required! Consult --help for more info.")

            targetOrder = args
        }

        // Installs the application on the linux system
        fun install(ignored: List<String>?) {
            throw NotImplementedError("The Arguments.install is not yet implemented!")
        }

        // Uninstalls the application from the linux system
        fun uninstall(ignored: List<String>?) {
            throw NotImplementedError("The Arguments.uninstall is not yet implemented!")
        }
    }
}

// Deserializes the resource into a Head object
fun deserialize(packageFile: String): Head {
    return try {
        ObjectMapper().readValue(URL(packageFile).readText().replace("\r", ""), Head::class.java)
    }
    // Invalid URL format treat as file
    catch(_: MalformedURLException) {
        ObjectMapper().readValue(File(packageFile).readText(), Head::class.java)
    }
}

// Runs an install script from a url
fun runWebScript(url: String, name: String) {
    try {
        // Creates a temporary directory
        val temp = File(System.getProperty("user.dir"), ".temp")
        if (temp.exists())
            temp.delete()

        temp.mkdir()
        println("Entering a temporary directory")

        // Downloads and runs the file
        File("${temp.path}/$name").writeText(URL(url).readText().replace("\r", ""))
        "chmod +x $name".runAsProcess(temp)
        "./.temp/$name".runAsProcess()
    }
    catch(e: Exception) {
        println("Exception caught: ${e.message}")
    }

    // Deletes the temporary directory
    File(System.getProperty("user.dir"), ".temp").deleteRecursively()
    println("Cleaning up..\n")
}

// Runs an install script locally
fun runLocalScript(path: String, name: String) {
    val dir = File(System.getProperty("user.dir"), path)
    "chmod +x $name".runAsProcess(dir)
    "./$name".runAsProcess(dir)
}

fun install(script: String) {
    // Deserializes the head object from the raw resource
    val head: Head = deserialize(script)
    
    // Asks for user permission
    //println("Proceed with the installation? [y\\n]")
    //print(">> ").also { System.out.flush() }
    //if (readLine().toString().toLowerCase()[0] == 'n') {
    //    println("\nTerminating installation")
    //    exitProcess(0)
    //}

    // TODO: Get priority queue
    val packages = mutableListOf<Head>()

    // Iterates over the custom packages and adds them to the queue if they're prioritized
    head.custom?.forEach { custom ->
        custom.local?.takeIf { it.prioritize }?.let { local -> packages.add(Head(custom = listOf(Custom(local = local)))) }
        custom.web?.takeIf { it.prioritize }?.let { web -> packages.add(Head(custom = listOf(Custom(web = web)))) }
    }

    // TODO: Remove everything within 'packages' from 'head' (no duplications)

    // Adds the package managers' packages
    head.packageManager?.forEach {
        it.packages.forEach { module ->

        }
        for (module in it.packages) {
            val command = "${if (it.sudo) "sudo " else ""}${it.type} ${module.flags.joinToString(" ")} ${module.app}"
            command.runAsProcess()
        }
    }


    /*
    val head = deserialize(raw)

    if (head.packageManager != null || head.custom != null)
        println("\nInstalling following packages:")

    // Prints the applications from the package manager list, if there is any
    head.packageManager?.forEach { manager ->
        manager.packages.forEach {
            println("\t${it.app}")
        }
    }

    // Prints the applications from the custom list, if there is any
    head.custom?.forEach {
        when {
            it.local != null -> println("\t${it.local.name}")
            it.web != null -> println("\t${it.web.url}")
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
            type.local != null -> runLocalScript(type.local.path, type.local.name)
            type.web != null ->  runWebScript(type.web.url, type.web.name)
            else -> throw RuntimeException("Invalid custom build type. Either specify 'local' or 'web'!")
        }
    }

    println("\nDone installing packages..")

     */
}

fun main() {
    /*
    * TODO:
    *
    * */

    val flags: HashMap<String, (List<String>?) -> Unit> = hashMapOf(
        "--help" to (Arguments)::help, "-h" to (Arguments)::help,
        "--target" to (Arguments)::target, "-t" to (Arguments)::target
    )

    if (args.isEmpty()) {
        throw RuntimeException("Invalid start arguments.. try running --help for more info!")
    }
    else {
        // Iterates over the extracted arguments
        val script: String = Arguments.extract(args).let { list ->
            list.forEach { unpacked ->
                if (unpacked.first.startsWith("--") || unpacked.first.startsWith("-")) {
                    // If an argument was found, force-invoke it
                    if (flags.contains(unpacked.first)) {
                        flags[unpacked.first]!!.invoke(unpacked.second)
                    }
                    // Otherwise, invalid command found
                    else {
                        println("Command '${unpacked.first.replace("--", "")}' not found..")
                    }
                }
            }

            // Looks for the install script
            list.forEach {
                // Tries to match against '-' and '--'
                Regex("^(?!-).*\$").find(it.first)?.value?.also { match ->
                    return@let match
                }
            }

            // No valid install script seems to have been passed
            throw RuntimeException("No valid install script seems to have been passed!")
        }

        install(script)
    }
}

fun test() {
    Arguments.extract(args).forEach { unpacked ->
        println("${unpacked.first}\t${unpacked.second}")
    }
}

main()