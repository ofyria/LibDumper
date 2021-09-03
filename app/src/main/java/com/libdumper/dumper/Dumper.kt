package com.libdumper.dumper

import com.libdumper.Utils.longToHex
import com.libdumper.fixer.Fixer
import java.io.*
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.nio.charset.Charset

/*
   An Modified Tools.kt from "https://github.com/BryanGIG/KMrite"
*/

class Dumper(private val nativeDir: String, pkg: String, private val file: String) {
    private val mem = Memory(pkg)

    fun dumpFile(autoFix: Boolean): String {
        var log = ""
        try {
            getProcessID()
            log += "PID : ${mem.pid}\n"
            parseMap()
            mem.size = mem.eAddress - mem.sAddress
            log += "Start Address : ${mem.sAddress.longToHex()}\n"
            log += "End Address : ${mem.eAddress.longToHex()}\n"
            log += "Size Memory : ${mem.size}\n"
            if (mem.sAddress > 1L && mem.eAddress > 1L) {
                val pathOut = File("/sdcard/Download/${mem.sAddress.longToHex()}-$file")
                RandomAccessFile("/proc/${mem.pid}/mem", "r").use { mems ->
                    mems.channel.use { filechannel ->
                        log += "Dumping...\n"
                        val buff = ByteBuffer.allocate(mem.size.toInt())
                        filechannel.read(buff, mem.sAddress)
                        pathOut.outputStream().use { out ->
                            out.write(buff.array())
                            out.close()
                        }
                        filechannel.close()
                        if (autoFix) {
                            log += "Fixing...\n"
                            log += "${Fixer(nativeDir, pathOut, mem).fixDump()}\n"
                        }
                        log += "Done. Saved at ${pathOut.absolutePath}\n\n"
                    }
                }
            }
        } catch (e: Exception) {
            log += "$e\n"
        }
        return log
    }

    private fun parseMap() {
        val files = File("/proc/${mem.pid}/maps")
        if (files.exists()) {
            val lines = files.readLines(Charset.defaultCharset())
            val startAddr = lines.find { it.contains(file) }
            val endAddr = lines.findLast { it.contains(file) }
            val regex = "\\p{XDigit}+-\\p{XDigit}+".toRegex()
            if (startAddr == null || endAddr == null) {
                throw FileNotFoundException("$file not found in ${files.path}")
            } else {
                startAddr.let { unused ->
                    regex.find(unused)?.value.let {
                        if (it != null) {
                            val result = it.split("-")
                            mem.sAddress = result[0].toLong(16)
                        }
                    }
                }
                endAddr.let { unused ->
                    regex.find(unused)?.value.let {
                        if (it != null) {
                            val result = it.split("-")
                            mem.eAddress = result[1].toLong(16)
                        }
                    }
                }
            }
        } else {
            throw FileNotFoundException("Failed To Open : ${files.path}")
        }
    }

    private fun getProcessID() {
        val process = Runtime.getRuntime().exec(arrayOf("pidof", mem.pkg))
        val reader = process.inputStream.bufferedReader()
        val buff = reader.readLine()
        reader.close()
        process.waitFor()
        process.destroy()
        if (buff != null && buff.isNotEmpty())
            mem.pid = buff.toInt()
        else
            throw IllegalArgumentException("Make sure your proccess package is running !\n")
    }
}

