package dev.cappyt.socialview

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ManifestQueriesTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    // Android 17 fails the install with INSTALL_PARSE_FAILED_MANIFEST_MALFORMED when one
    // <queries> intent declares two schemes.
    @Test
    fun `every queries intent declares at most one data scheme`() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifestFile())
        val intents = document.getElementsByTagName("intent")
        assertTrue("Expected at least one queries intent", intents.length > 0)

        for (index in 0 until intents.length) {
            val intent = intents.item(index) as Element
            val schemes = intent.getElementsByTagName("data")
                .let { nodes ->
                    (0 until nodes.length).mapNotNull { position ->
                        (nodes.item(position) as Element)
                            .getAttributeNS(androidNamespace, "scheme")
                            .takeIf { it.isNotEmpty() }
                    }
                }
            assertTrue(
                "queries intent declares schemes $schemes",
                schemes.size <= 1,
            )
        }
        assertEquals(2, intents.length)
    }

    private fun manifestFile(): File =
        listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")
}