#!/usr/bin/env kotlin

/* -- Runs the following in a temporary directory --
* git clone https://aur.archlinux.org/yay.git yay
* makepkg -si
*/

import java.io.File
import java.util.concurrent.TimeUnit

fun String.runAsProcess(workingDir: File = File(System.getProperty("user.dir"))) {
    ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(1, TimeUnit.HOURS)
}

if (File(System.getProperty("user.dir"), ".temp").exists())
    "rm -rf .temp".runAsProcess()

if (!File(System.getProperty("user.dir"), ".temp").mkdir())
    throw RuntimeException("Failed to create the build directory")

try {
    "git clone https://aur.archlinux.org/yay.git yay".runAsProcess(File(System.getProperty("user.dir"), ".temp"))
    "makepkg -si --needed --noconfirm".runAsProcess(File(System.getProperty("user.dir"), ".temp/yay"))
}
catch (e: Exception) {
    println(e.message)
}

if (File(System.getProperty("user.dir"), ".temp").exists())
    "rm -rf .temp".runAsProcess()