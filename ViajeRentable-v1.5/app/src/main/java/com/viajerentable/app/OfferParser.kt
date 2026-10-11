package com.viajerentable.app

data class ParsedOffer(val fare: Double, val pickupKm: Double? = null, val pickupMinutes: Double? = null, val tripKm: Double? = null, val tripMinutes: Double? = null, val pickupPlace: String? = null, val destinationPlace: String? = null) {
    val hasRouteDetails: Boolean get() = pickupKm != null && pickupMinutes != null && tripKm != null && tripMinutes != null
}
object OfferParser {
    private val fareRegex = Regex("""(?:CRC\s*|₡\s*|\$\s*)([0-9]+(?:[,.][0-9]+)*)""", RegexOption.IGNORE_CASE)
    private val legRegex = Regex("""((?:[0-9]{1,3}\s*(?:h|horas?)\s*)?(?:[0-9]{1,3}\s*(?:min(?:utos?)?|mins?)\b)?)\s*[^|]{0,80}?\(([0-9]+(?:[.,][0-9]+)?)\s*(km|m)\)""", RegexOption.IGNORE_CASE)
    private val hourRegex = Regex("""([0-9]{1,3})\s*(?:h|horas?)""", RegexOption.IGNORE_CASE)
    private val minuteRegex = Regex("""([0-9]{1,3})\s*(?:min(?:utos?)?|mins?)\b""", RegexOption.IGNORE_CASE)
    fun containsFare(rawText: String) = fareRegex.containsMatchIn(rawText)
    fun containsRouteLeg(rawText: String) = legRegex.findAll(rawText).count() >= 2
    fun parse(rawText: String): ParsedOffer? {
        if (!Regex("""\b(?:Aceptar|Me\s+interesa)\b""", RegexOption.IGNORE_CASE).containsMatchIn(rawText)) return null
        val text = rawText.replace(Regex("\\s+"), " ").trim()
        val segments = text.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val fm = fareRegex.find(text) ?: return null
        val fare = parseNumber(fm.groupValues[1]) ?: return null
        if (fare < 300.0 || fare > 200000.0) return null
        val legs = legRegex.findAll(text).toList()
        if (legs.size < 2) return ParsedOffer(fare)
        val p = legs[0]; val t = legs[1]
        val pmin = parseDuration(p.groupValues[1]) ?: return ParsedOffer(fare)
        val pd = parseNumber(p.groupValues[2]) ?: return ParsedOffer(fare)
        val pk = if (p.groupValues[3].equals("m", true)) pd / 1000.0 else pd
        val tmin = parseDuration(t.groupValues[1]) ?: return ParsedOffer(fare)
        val td = parseNumber(t.groupValues[2]) ?: return ParsedOffer(fare)
        val tk = if (t.groupValues[3].equals("m", true)) td / 1000.0 else td
        if (pmin < 0 || pk < 0 || tmin <= 0 || tk <= 0) return ParsedOffer(fare)
        val pi = segments.indexOfFirst { legRegex.containsMatchIn(it) }; val ti = segments.indexOfLast { legRegex.containsMatchIn(it) }
        return ParsedOffer(fare,pk,pmin,tk,tmin,findPlace(segments,pi+1,ti),findPlace(segments,ti+1,segments.size))
    }
    private fun parseDuration(s:String):Double? {
        val h=hourRegex.find(s)?.groupValues?.get(1)?.toDoubleOrNull()?:0.0
        val m=minuteRegex.find(s)?.groupValues?.get(1)?.toDoubleOrNull()?:0.0
        return if(h==0.0&&m==0.0)null else h*60.0+m
    }
    private fun findPlace(parts:List<String>,start:Int,end:Int):String? {
        for(i in start until end.coerceAtMost(parts.size)) {
            val s=parts[i].trim()
            if(s.length !in 3..100||fareRegex.containsMatchIn(s)||legRegex.containsMatchIn(s))continue
            if(s.contains("aceptar",true)||s.contains("rechazar",true)||s.contains("interesa",true))continue
            if(s.equals("UberX",true)||s.equals("Exclusivo",true)||s.equals("Viaje",true)||s.matches(Regex("[0-9.,() ]+")))continue
            return s
        }; return null
    }
    internal fun parseNumber(input:String):Double? {
        val v=input.trim().trimEnd('.',',')
        val n=if(v.contains(',')&&v.contains('.')) { if(v.lastIndexOf('.')>v.lastIndexOf(','))v.replace(",","") else v.replace(".","").replace(",",".") }
        else if(v.contains(',')){if(v.substringAfterLast(',').length in 1..2)v.replace(",",".") else v.replace(",","")}
        else if(v.contains('.')){if(v.substringAfterLast('.').length==3&&v.substringBeforeLast('.').isNotEmpty())v.replace(".","")else v}
        else v
        return n.toDoubleOrNull()
    }
}