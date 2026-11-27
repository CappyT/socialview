package dev.cappyt.socialview.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostIdentityTest {
    @Test
    fun `an author equal to the handle is not shown twice`() {
        assertNull(PostIdentity.displayAuthor("ilsecoloxix", "@ilsecoloxix"))
        assertNull(PostIdentity.displayAuthor("@ilsecoloxix", "@ILSECOLOXIX"))
    }

    @Test
    fun `a different author and handle are both kept`() {
        assertEquals(
            "Il Secolo XIX",
            PostIdentity.displayAuthor("Il Secolo XIX", "@ilsecoloxix"),
        )
        assertEquals("alice", PostIdentity.displayAuthor("alice", null))
        assertNull(PostIdentity.displayAuthor(null, "@alice"))
        assertNull(PostIdentity.displayAuthor("  ", "@alice"))
    }
}