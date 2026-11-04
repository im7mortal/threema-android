package ch.threema.app.license

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerUrlParameterTest {

    private val source = ParameterSource.USER

    @Test
    fun `create returns null for empty string`() {
        assertNull(ServerUrlParameter.create("", source))
    }

    @Test
    fun `create returns null for whitespace only string`() {
        assertNull(ServerUrlParameter.create("   ", source))
    }

    @Test
    fun `create returns null for tab only string`() {
        assertNull(ServerUrlParameter.create("\t", source))
    }

    @Test
    fun `create returns null for newline only string`() {
        assertNull(ServerUrlParameter.create("\n", source))
    }

    @Test
    fun `create trims leading and trailing whitespace`() {
        val result = ServerUrlParameter.create("   example.org   ", source)
        assertEquals("example.org", result?.value)
    }

    @Test
    fun `create strips https prefix`() {
        val result = ServerUrlParameter.create("https://example.org", source)
        assertEquals("example.org", result?.value)
    }

    @Test
    fun `create strips http prefix`() {
        val result = ServerUrlParameter.create("http://example.org", source)
        assertEquals("example.org", result?.value)
    }

    @Test
    fun `create strips https prefix case-insensitively`() {
        val result = ServerUrlParameter.create("HTTPS://example.org", source)
        assertEquals("example.org", result?.value)
    }

    @Test
    fun `create strips http prefix case-insensitively`() {
        val result = ServerUrlParameter.create("HTTP://example.org", source)
        assertEquals("example.org", result?.value)
    }

    @Test
    fun `create leaves url unchanged when no scheme present`() {
        val result = ServerUrlParameter.create("example.org", source)
        assertEquals("example.org", result?.value)
    }

    @Test
    fun `create strips default oppf suffix`() {
        val result = ServerUrlParameter.create("example.org/prov/config.oppf", source)
        assertEquals("example.org", result?.value)
    }

    @Test
    fun `create does not strip a custom oppf suffix that is not the default path`() {
        val result = ServerUrlParameter.create("example.org/custom.oppf", source)
        assertEquals("example.org/custom.oppf", result?.value)
    }

    @Test
    fun `create strips scheme and default oppf suffix together`() {
        val result = ServerUrlParameter.create("https://example.org/prov/config.oppf", source)
        assertEquals("example.org", result?.value)
    }

    @Test
    fun `create trims whitespace around scheme and suffix stripping`() {
        val result = ServerUrlParameter.create("  https://example.org/prov/config.oppf  ", source)
        assertEquals("example.org", result?.value)
    }

    @Test
    fun `create handles trailing whitespace left after suffix removal`() {
        // "example.org /prov/config.oppf" -> removeSuffix won't match due to space,
        // so value should retain the raw remainder, then get trimmed.
        val result = ServerUrlParameter.create("example.org/prov/config.oppf   ", source)
        assertEquals("example.org", result?.value)
    }

    @Test
    fun `create sets the provided parameter source`() {
        val result = ServerUrlParameter.create("example.org", source)
        assertEquals(source, result?.source)
    }

    @Test
    fun `getUrlToOppf adds https prefix and default oppf path`() {
        val parameter = ServerUrlParameter.create("example.org", source)
        assertEquals("https://example.org/prov/config.oppf", parameter?.getUrlToOppf())
    }

    @Test
    fun `getUrlToOppf works when original input had https scheme and default suffix`() {
        val parameter = ServerUrlParameter.create("https://example.org/prov/config.oppf", source)
        assertEquals("https://example.org/prov/config.oppf", parameter?.getUrlToOppf())
    }

    @Test
    fun `getUrlToOppf works when original input had http scheme`() {
        val parameter = ServerUrlParameter.create("http://example.org", source)
        assertEquals("https://example.org/prov/config.oppf", parameter?.getUrlToOppf())
    }

    @Test
    fun `getUrlToOppf does not duplicate oppf path when value already ends with a custom oppf suffix`() {
        val parameter = ServerUrlParameter.create("example.org/custom.oppf", source)
        assertEquals("https://example.org/custom.oppf", parameter?.getUrlToOppf())
    }

    @Test
    fun `getUrlToOppf trims value before building url`() {
        val parameter = ServerUrlParameter.create("  example.org  ", source)
        assertEquals("https://example.org/prov/config.oppf", parameter?.getUrlToOppf())
    }

    @Test
    fun `getUrlToOppf handles url with path segments`() {
        val parameter = ServerUrlParameter.create("example.org/some/path", source)
        assertEquals("https://example.org/some/path/prov/config.oppf", parameter?.getUrlToOppf())
    }
}
