package de.fgchamburg.vodhelper.app

import de.fgchamburg.vodhelper.core.tools.HostOs
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

/** Asks the user for a folder. Tests pass a fake. */
fun interface FolderPicker {
    /** Returns the chosen folder's absolute path, or null when the user cancelled. */
    fun pick(title: String, startIn: String?): String?
}

/** The system's folder dialog. Modal, so call it from the UI thread. */
object SystemFolderPicker : FolderPicker {
    private const val DIRECTORIES_PROPERTY = "apple.awt.fileDialogForDirectories"

    override fun pick(title: String, startIn: String?): String? =
        if (HostOs.current() == HostOs.MAC) pickOnMac(title, startIn) else pickWithSwing(title, startIn)

    private fun pickOnMac(title: String, startIn: String?): String? {
        System.setProperty(DIRECTORIES_PROPERTY, "true")
        try {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
            startIn?.let { dialog.directory = it }
            dialog.isVisible = true
            val file = dialog.file ?: return null
            return File(dialog.directory, file).absolutePath
        } finally {
            System.setProperty(DIRECTORIES_PROPERTY, "false")
        }
    }

    private fun pickWithSwing(title: String, startIn: String?): String? {
        val chooser = JFileChooser(startIn)
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = title
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
    }
}
