package com.duta.movie.provider

import com.duta.movie.data.local.InstalledProviderEntity
import com.duta.movie.provider.diagnostic.EndpointStatus
import com.duta.movie.provider.diagnostic.EndpointTestResult
import com.duta.movie.provider.diagnostic.ProviderDiagnosticManager
import com.duta.movie.provider.diagnostic.ProviderDiagnosticSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDiagnosticTest {

    @Test
    fun testCandidateEndpointsParsing() {
        val lk21 = InstalledProviderEntity(
            id = "com.duta.provider.lk21",
            repoId = "official",
            name = "LK21",
            displayName = "LK21 / LayarKaca21",
            baseUrlsJson = "[\"https://inlionsforisbvi.org\", \"https://bullerswood.org\"]"
        )

        val endpoints = ProviderDiagnosticManager.getCandidateEndpoints(lk21)
        assertTrue("Must include baseUrlsJson entries", endpoints.contains("https://inlionsforisbvi.org"))
        assertTrue("Must include fallback cluster endpoints", endpoints.contains("https://scphi.org"))
        assertTrue("Endpoints must be distinct", endpoints.distinct().size == endpoints.size)
    }

    @Test
    fun testPencuriCandidateEndpoints() {
        val pencuri = InstalledProviderEntity(
            id = "com.duta.provider.pencurimovie",
            repoId = "official",
            name = "PencuriMovie",
            displayName = "PencuriMovie (Official)",
            baseUrlsJson = "[\"https://ww44.pencurimovie.baby\"]"
        )

        val endpoints = ProviderDiagnosticManager.getCandidateEndpoints(pencuri)
        assertTrue("Must contain base URL", endpoints.contains("https://ww44.pencurimovie.baby"))
        assertTrue("Must contain fallback mirrors", endpoints.contains("https://pencurimovie.baby"))
        assertTrue("Must contain fallback mirrors", endpoints.contains("https://ww45.pencurimovie.baby"))
    }

    @Test
    fun testDiagnosticSummaryCounts() {
        val results = listOf(
            EndpointTestResult("p1", "Prov 1", "https://site1.com", EndpointStatus.ACTIVE, 200, 100),
            EndpointTestResult("p1", "Prov 1", "https://site2.com", EndpointStatus.ACTIVE, 200, 150),
            EndpointTestResult("p1", "Prov 1", "https://site3.com", EndpointStatus.REDIRECTED, 301, 80, "https://site3-new.com"),
            EndpointTestResult("p1", "Prov 1", "https://site4.com", EndpointStatus.DEAD, 404, 300),
            EndpointTestResult("p1", "Prov 1", "https://site5.com", EndpointStatus.TIMEOUT, null, 3500)
        )

        val summary = ProviderDiagnosticSummary(
            providerId = "p1",
            providerName = "Prov 1",
            endpoints = results
        )

        assertEquals(5, summary.totalCount)
        assertEquals(2, summary.activeCount)
        assertEquals(1, summary.redirectedCount)
        assertEquals(1, summary.deadCount)
        assertEquals(1, summary.timeoutCount)
    }
}
