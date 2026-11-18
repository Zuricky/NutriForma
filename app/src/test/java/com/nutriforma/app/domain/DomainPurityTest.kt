package com.nutriforma.app.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Ensures `domain/` stays pure Kotlin with zero `android.*` / `androidx.*` imports. */
class DomainPurityTest {

    @Test
    fun domainPackageHasNoAndroidImports() {
        val root = File("src/main/java/com/nutriforma/app/domain")
        assertTrue("Cartella domain non trovata: ${root.absolutePath}", root.isDirectory)
        val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Nessun sorgente trovato in domain/", sources.isNotEmpty())
        val offenders = sources.flatMap { file ->
            file.readLines()
                .filter { line -> line.trim().let { it.startsWith("import android.") || it.startsWith("import androidx.") } }
                .map { "${file.name}: $it" }
        }
        assertTrue("Import Android in domain/: $offenders", offenders.isEmpty())
    }
}
