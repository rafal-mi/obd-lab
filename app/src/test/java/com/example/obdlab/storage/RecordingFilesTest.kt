package com.example.obdlab.storage

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingFilesTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun repeatedTimestampsNeverOverwrite() {
        val store = RecordingFiles(folder.root)
        val a = store.save(0, "first", "log1")
        val b = store.save(0, "second", "log2")
        assertNotEquals(a.csv.name, b.csv.name)
        assertEquals("first", a.csv.readText())
        assertEquals("second", b.csv.readText())
        assertEquals("log1", a.log.readText())
        assertEquals(2, RecordingFiles(folder.root).list().size)
    }
}
