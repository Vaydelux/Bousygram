package com.vaydelux.bousygram.core.cast
import java.io.*
import java.util.Collections
fun main(args: Array<String>) {
    val dir = File(args[0]); val hls = File(dir,"hls")
    val init = File(hls,"init.mp4"); val segments = hls.listFiles()!!.filter { it.extension == "m4s" }.sortedBy { it.name }
    check(segments.size == 4)
    SequenceInputStream(Collections.enumeration((listOf(init)+segments).map { it.inputStream() })).use { stream ->
        val reader = RollingFmp4Framing(stream) { true }
        check(reader.readInit().contentEquals(init.readBytes()))
        for ((index,file) in segments.withIndex()) {
            val fragment = reader.readFragment()!!
            check(fragment.sequence == index+1L)
            check(fragment.bytes.contentEquals(file.readBytes()))
        }
        check(reader.readFragment() == null)
    }
    val aac = File(dir,"source.aac").readBytes(); var offset = 0; var count = 0
    var config: RollingHlsAac.Config? = null
    while (offset < aac.size) {
        check(aac.size - offset >= 7)
        fun b(i:Int)=aac[offset+i].toInt() and 255
        val length=((b(3) and 3) shl 11) or (b(4) shl 3) or (b(5) shr 5)
        check(length >= 8 && offset+length <= aac.size)
        val normalized=RollingHlsAac.normalize(aac.copyOfRange(offset,offset+length),config)!!
        config=normalized.config
        check(normalized.payload.contentEquals(aac.copyOfRange(offset+7,offset+length)))
        offset+=length; count++
    }
    check(count > 0)
    println("PASS: exact init and ${segments.size} real fMP4 fragments, ${count} AAC access units; config=$config")
    val tail=segments.first().readBytes(); val malformed=RollingFmp4Framing(ByteArrayInputStream(init.readBytes()+tail.copyOf(tail.size-1))) { true }
    malformed.readInit(); try { malformed.readFragment(); error("truncation accepted") } catch (_:IOException) { println("PASS: real truncated mdat rejected") }
}
