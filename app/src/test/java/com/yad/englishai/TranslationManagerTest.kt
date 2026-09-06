package com.yad.englishai

import org.junit.Test
import org.junit.Assert.*

class TranslationManagerTest {
    
    @Test
    fun testIsValidText() {
        val manager = TranslationManager(mockContext())
        
        assertTrue(manager.isValidText("Hello"))
        assertTrue(manager.isValidText("Test translation"))
        assertFalse(manager.isValidText(""))
        assertFalse(manager.isValidText("   "))
        assertFalse(manager.isValidText("a".repeat(6000)))
    }
    
    @Test
    fun testTranslationResult() {
        val success = TranslationManager.TranslationResult.Success("Halo")
        val error = TranslationManager.TranslationResult.Error("Gagal")
        
        assertEquals("Halo", (success as TranslationManager.TranslationResult.Success).text)
        assertEquals("Gagal", (error as TranslationManager.TranslationResult.Error).message)
    }
    
    private fun mockContext(): android.content.Context {
        return androidx.test.core.app.ApplicationProvider.getApplicationContext()
    }
}

class NetworkManagerTest {
    
    @Test
    fun testNetworkCheck() {
        val manager = NetworkManager(mockContext())
        // Network check should not throw
        val result = manager.isNetworkAvailable()
        assertTrue(result is Boolean)
    }
    
    private fun mockContext(): android.content.Context {
        return androidx.test.core.app.ApplicationProvider.getApplicationContext()
    }
}