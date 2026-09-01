package org.example.fastfinder.ui

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import kotlin.test.Test
import kotlin.test.assertEquals

class ResultItemTest {

    @Test
    fun `copyPathToClipboard puts the exact path on the system clipboard`() {
        val path = "C:\\Users\\test\\Documents\\report 2024.pdf"

        copyPathToClipboard(path)

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        assertEquals(path, clipboard.getData(DataFlavor.stringFlavor))
    }
}
