package com.xuhuangbin.xinghuozhaidu.data.content

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentVersionTest {
    @Test
    fun comparesSemanticVersionComponentsNumerically() {
        assertEquals(1, ContentVersion.compare("1.10.0", "1.2.9"))
        assertEquals(-1, ContentVersion.compare("1.0.9", "1.1.0"))
        assertEquals(0, ContentVersion.compare("2.0.0", "2.0.0"))
    }

    @Test(expected = ContentPackageException::class)
    fun rejectsNonSemanticVersions() {
        ContentVersion.compare("1.0", "1.0.0")
    }
}
