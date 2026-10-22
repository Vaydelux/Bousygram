package com.vaydelux.bousygram.core.tdlib.server
import com.vaydelux.bousygram.core.tdlib.fastlink.FastLinkUrlValidator
import com.vaydelux.bousygram.core.tdlib.auth.TdlibDatabaseKeyPolicy

/** Standalone smoke tests for Android stream utilities; does not require Android SDK/JUnit. */
fun main() {
    var passed = 0
    fun test(name: String, block: () -> Unit) {
        block()
        println("PASS: $name")
        passed++
    }

    test("no Range requests full file") {
        check(HttpRangeParser.parse(null, 100) ==
            HttpRangeParser.Result.Valid(HttpRangeParser.Range(0, 99, false)))
    }
    test("closed range parses") {
        check(HttpRangeParser.parse("bytes=10-19", 100) ==
            HttpRangeParser.Result.Valid(HttpRangeParser.Range(10, 19, true)))
    }
    test("open-ended range parses") {
        check(HttpRangeParser.parse("bytes=90-", 100) ==
            HttpRangeParser.Result.Valid(HttpRangeParser.Range(90, 99, true)))
    }
    test("suffix range parses") {
        check(HttpRangeParser.parse("bytes=-10", 100) ==
            HttpRangeParser.Result.Valid(HttpRangeParser.Range(90, 99, true)))
    }
    test("requested end is clamped") {
        check(HttpRangeParser.parse("bytes=90-999", 100) ==
            HttpRangeParser.Result.Valid(HttpRangeParser.Range(90, 99, true)))
    }
    test("multi-range request is rejected") {
        check(HttpRangeParser.parse("bytes=0-1,4-5", 100) == HttpRangeParser.Result.Unsatisfiable)
    }
    test("out-of-range start is rejected") {
        check(HttpRangeParser.parse("bytes=100-", 100) == HttpRangeParser.Result.Unsatisfiable)
    }
    test("reversed range is rejected") {
        check(HttpRangeParser.parse("bytes=20-10", 100) == HttpRangeParser.Result.Unsatisfiable)
    }
    test("oversized number is rejected") {
        check(HttpRangeParser.parse("bytes=999999999999999999999999-", 100) == HttpRangeParser.Result.Unsatisfiable)
    }
    test("file token is scoped to one file") {
        val token = StreamAccessRegistry.issueFile(123)
        check(StreamAccessRegistry.authorizesFile(token, 123))
        check(!StreamAccessRegistry.authorizesFile(token, 456))
        check(!StreamAccessRegistry.authorizesFile(null, 123))
        check(!StreamAccessRegistry.authorizesPlaylist(token))
    }
    test("playlist token cannot authorize a file") {
        val token = StreamAccessRegistry.issuePlaylist()
        check(StreamAccessRegistry.authorizesPlaylist(token))
        check(!StreamAccessRegistry.authorizesFile(token, 123))
    }
    test("revoked token immediately loses access") {
        val token = StreamAccessRegistry.issueFile(987)
        check(StreamAccessRegistry.authorizesFile(token, 987))
        check(StreamAccessRegistry.revoke(token))
        check(!StreamAccessRegistry.authorizesFile(token, 987))
        check(!StreamAccessRegistry.revoke(token))
    }
    test("capability is extracted and revoked from local URL") {
        val token = StreamAccessRegistry.issueFile(654)
        check(StreamAccessRegistry.revokeFromUrl("http://127.0.0.1:8080/stream?fileId=654&token=$token"))
        check(!StreamAccessRegistry.authorizesFile(token, 654))
    }
    test("remote URLs cannot revoke local capabilities") {
        val token = StreamAccessRegistry.issueFile(655)
        check(!StreamAccessRegistry.revokeFromUrl("https://example.invalid/stream?fileId=655&token=$token"))
        check(StreamAccessRegistry.authorizesFile(token, 655))
        StreamAccessRegistry.revoke(token)
    }

    test("Fast-Link validator accepts HTTPS URL") {
        check(FastLinkUrlValidator.validate("https://media.example.com/watch?id=abc") ==
            "https://media.example.com/watch?id=abc")
    }
    test("Fast-Link validator rejects HTTP URL") {
        check(FastLinkUrlValidator.validate("http://media.example.com/watch") == null)
    }
    test("Fast-Link validator rejects credentials in URL") {
        check(FastLinkUrlValidator.validate("https://user:pass@media.example.com/watch") == null)
    }
    test("Fast-Link validator rejects non-standard ports") {
        check(FastLinkUrlValidator.validate("https://media.example.com:8443/watch") == null)
    }
    test("Fast-Link validator rejects missing host and malformed URLs") {
        check(FastLinkUrlValidator.validate("https:///watch") == null)
        check(FastLinkUrlValidator.validate("this is not a URL") == null)
    }
    test("Fast-Link validator rejects URL fragments") {
        check(FastLinkUrlValidator.validate("https://media.example.com/watch#token") == null)
    }

    test("database policy creates an encrypted key for new database") {
        check(TdlibDatabaseKeyPolicy.decide(null, false, false) ==
            TdlibDatabaseKeyPolicy.Decision.CREATE_ENCRYPTED_KEY)
    }
    test("database policy preserves legacy database with no recorded key") {
        check(TdlibDatabaseKeyPolicy.decide(null, false, true) ==
            TdlibDatabaseKeyPolicy.Decision.USE_LEGACY_EMPTY_KEY)
    }
    test("database policy uses stored encrypted key when mode marker is absent") {
        check(TdlibDatabaseKeyPolicy.decide(null, true, false) ==
            TdlibDatabaseKeyPolicy.Decision.USE_STORED_ENCRYPTED_KEY)
    }
    test("database policy fails closed when encrypted key is lost") {
        check(TdlibDatabaseKeyPolicy.decide(TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1, false, true) ==
            TdlibDatabaseKeyPolicy.Decision.FAIL_CLOSED)
    }

    test("database policy fails closed for conflicting legacy mode and encrypted key") {
        check(TdlibDatabaseKeyPolicy.decide(TdlibDatabaseKeyPolicy.MODE_LEGACY_EMPTY, true, true) ==
            TdlibDatabaseKeyPolicy.Decision.FAIL_CLOSED)
    }

    test("database-key mode resolver accepts matching redundant markers") {
        check(TdlibDatabaseKeyPolicy.resolveMode(TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1,
            TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1) == TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1)
    }
    test("database-key mode resolver rejects conflicting markers") {
        check(TdlibDatabaseKeyPolicy.resolveMode(TdlibDatabaseKeyPolicy.MODE_ENCRYPTED_V1,
            TdlibDatabaseKeyPolicy.MODE_LEGACY_EMPTY) == TdlibDatabaseKeyPolicy.MODE_INCONSISTENT)
        check(TdlibDatabaseKeyPolicy.decide(TdlibDatabaseKeyPolicy.MODE_INCONSISTENT, false, true) ==
            TdlibDatabaseKeyPolicy.Decision.FAIL_CLOSED)
    }

    println("RESULT: $passed/27 passed")
}
