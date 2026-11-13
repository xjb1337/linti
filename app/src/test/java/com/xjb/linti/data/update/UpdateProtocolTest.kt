package com.xjb.linti.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateProtocolTest {

    @Test
    fun `semantic versions parse and compare numerically`() {
        assertEquals(SemanticVersion(1, 10, 2), UpdateProtocol.parseVersion(" 1.10.2 "))
        assertEquals(SemanticVersion(0, 3, 0), UpdateProtocol.parseTag("v0.3.0"))
        assertTrue(UpdateProtocol.parseVersion("1.10.0")!! > UpdateProtocol.parseVersion("1.9.9")!!)
        assertTrue(UpdateProtocol.isNewer("0.3.0", "0.2.0"))
        assertFalse(UpdateProtocol.isNewer("0.2.0", "0.2.0"))
        assertFalse(UpdateProtocol.isNewer("0.1.9", "0.2.0"))
    }

    @Test
    fun `invalid version formats are rejected`() {
        assertNull(UpdateProtocol.parseVersion("v0.3.0"))
        assertNull(UpdateProtocol.parseVersion("0.3"))
        assertNull(UpdateProtocol.parseVersion("0.3.0-beta"))
        assertNull(UpdateProtocol.parseTag("0.3.0"))
        assertNull(UpdateProtocol.parseTag("release-v0.3.0"))
        assertThrows(UpdateException::class.java) {
            UpdateProtocol.isNewer("0.3", "0.2.0")
        }
    }

    @Test
    fun `release parses expected assets and plain text notes`() {
        val release = UpdateProtocol.parseRelease(
            releaseJson(
                tag = "v1.4.2",
                body = "# 更新内容\n\n- **修复** [登录](https://example.com) 问题\n- `UI` 调整",
            ),
        )

        assertEquals(SemanticVersion(1, 4, 2), release.version)
        assertEquals("https://github.com/xjb1337/linti/releases/tag/v1.4.2", release.releaseUrl)
        assertEquals(
            "https://github.com/xjb1337/linti/releases/download/v1.4.2/linti-1.4.2.apk",
            release.apkUrl,
        )
        assertEquals(
            "https://github.com/xjb1337/linti/releases/download/v1.4.2/linti-1.4.2.apk.sha256",
            release.checksumUrl,
        )
        assertEquals(12_345L, release.apkSize)
        assertEquals("更新内容\n\n• 修复 登录 问题\n• UI 调整", release.notes)
        assertFalse(release.notes.contains("**"))
        assertFalse(release.notes.contains("]("))
    }

    @Test
    fun `release rejects drafts prereleases and missing assets`() {
        assertFailure(UpdateFailure.INVALID_RELEASE) {
            UpdateProtocol.parseRelease(releaseJson(tag = "v1.4.2", draft = true))
        }
        assertFailure(UpdateFailure.INVALID_RELEASE) {
            UpdateProtocol.parseRelease(releaseJson(tag = "v1.4.2", prerelease = true))
        }
        assertFailure(UpdateFailure.INVALID_RELEASE) {
            UpdateProtocol.parseRelease(releaseJson(tag = "1.4.2"))
        }
        assertFailure(UpdateFailure.MISSING_ASSET) {
            UpdateProtocol.parseRelease(
                releaseJson(tag = "v1.4.2", checksumAsset = false),
            )
        }
    }

    @Test
    fun `release rejects insecure download urls`() {
        assertFailure(UpdateFailure.INVALID_RELEASE) {
            UpdateProtocol.parseRelease(
                releaseJson(
                    tag = "v1.4.2",
                    apkUrl = "http://github.com/xjb1337/linti/releases/download/v1.4.2/linti-1.4.2.apk",
                ),
            )
        }
    }

    @Test
    fun `sha256 files and digests are parsed consistently`() {
        val digest = "6deb2e6b2049a4ee785be244a0bcf60c90293975fbb154040aaaf9dda45fec1b"
        assertEquals(digest, UpdateProtocol.parseChecksum("$digest  linti-0.2.0.apk\n"))
        assertEquals(digest, UpdateProtocol.parseChecksum("$digest\tlinti-0.2.0.apk\n"))
        assertEquals(digest, UpdateProtocol.parseChecksum(digest.uppercase()))
        assertEquals(
            digest,
            UpdateProtocol.normalizeSha256(digest.chunked(2).joinToString(":")),
        )
        assertTrue(UpdateProtocol.checksumMatches(digest, digest.uppercase()))
        assertFalse(UpdateProtocol.checksumMatches("not-a-digest", digest))
        assertNull(UpdateProtocol.parseChecksum(""))
    }

    @Test
    fun `markdown notes become readable plain text`() {
        val markdown = """
            ## v0.3.0

            > 本次更新

            - [x] **修复** `深色模式`
            - [ ] [新页面](https://example.com/page)

            ```
            adb install linti.apk
            ```
        """.trimIndent()

        assertEquals(
            "v0.3.0\n\n本次更新\n\n☑ 修复 深色模式\n☐ 新页面\n\nadb install linti.apk",
            UpdateProtocol.plainTextNotes(markdown),
        )
    }

    private fun assertFailure(expected: UpdateFailure, block: () -> Unit) {
        val error = assertThrows(UpdateException::class.java, block)
        assertEquals(expected, error.failure)
    }

    private fun releaseJson(
        tag: String,
        body: String = "更新",
        draft: Boolean = false,
        prerelease: Boolean = false,
        apkUrl: String = "https://github.com/xjb1337/linti/releases/download/$tag/linti-${tag.removePrefix("v")}.apk",
        checksumAsset: Boolean = true,
    ): String {
        val version = tag.removePrefix("v")
        val checksum = if (checksumAsset) {
            """,
                {
                  "name": "linti-$version.apk.sha256",
                  "browser_download_url": "https://github.com/xjb1337/linti/releases/download/$tag/linti-$version.apk.sha256",
                  "size": 96
                }"""
        } else {
            ""
        }
        return """
            {
              "tag_name": "$tag",
              "draft": $draft,
              "prerelease": $prerelease,
              "body": ${org.json.JSONObject.quote(body)},
              "html_url": "https://github.com/xjb1337/linti/releases/tag/$tag",
              "assets": [
                {
                  "name": "linti-$version.apk",
                  "browser_download_url": "$apkUrl",
                  "size": 12345
                }$checksum
              ]
            }
        """.trimIndent()
    }
}
