package io.github.c1921.namingdict.data

import io.github.c1921.namingdict.data.model.GivenNameMode
import io.github.c1921.namingdict.data.model.NamingGender
import io.github.c1921.namingdict.data.model.NamingScheme
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavRepositoryTest {

    @Test
    fun uploadFavorites_successBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(201))
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.uploadFavorites(
                config = config,
                payload = FavoritesSyncPayload(updatedAt = 1_700_000_000_000, favoriteOrder = listOf(3, 1))
            )

            assertTrue(result.success)
            assertTrue(result.message.isNotBlank())

            val mkcolRequest = server.takeRequest()
            assertEquals("MKCOL", mkcolRequest.method)
            val uploadRequest = server.takeRequest()
            assertEquals("PUT", uploadRequest.method)
            assertTrue(uploadRequest.path.orEmpty().endsWith("/NamingDict/favorites.json"))
        }
    }

    @Test
    fun uploadFavorites_httpFailureBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(500))
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.uploadFavorites(
                config = config,
                payload = FavoritesSyncPayload(updatedAt = 1_700_000_000_000, favoriteOrder = listOf(1))
            )

            assertFalse(result.success)
            assertTrue(result.message.contains("HTTP 500"))
        }
    }

    @Test
    fun uploadNamePlans_successBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(201))
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.uploadNamePlans(
                config = config,
                payload = NamePlansSyncPayload(
                    updatedAt = 1_700_000_000_000,
                    surname = "OuYang",
                    schemes = listOf(
                        NamingScheme(
                            id = 10L,
                            givenNameMode = GivenNameMode.Double,
                            gender = NamingGender.Male,
                            slot1 = "A",
                            slot2 = "B"
                        )
                    )
                )
            )

            assertTrue(result.success)
            assertTrue(result.message.isNotBlank())

            val mkcolRequest = server.takeRequest()
            assertEquals("MKCOL", mkcolRequest.method)
            val uploadRequest = server.takeRequest()
            assertEquals("PUT", uploadRequest.method)
            assertTrue(uploadRequest.path.orEmpty().endsWith("/NamingDict/name_plans.json"))
            assertTrue(uploadRequest.body.readUtf8().contains("\"gender\":\"Male\""))
        }
    }

    @Test
    fun uploadNamePlans_httpFailureBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(500))
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.uploadNamePlans(
                config = config,
                payload = NamePlansSyncPayload(surname = "Zhang", schemes = emptyList())
            )

            assertFalse(result.success)
            assertTrue(result.message.contains("HTTP 500"))
        }
    }

    @Test
    fun downloadFavorites_non2xxBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.downloadFavorites(config)

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }
    }

    @Test
    fun downloadFavorites_emptyContentBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(""))
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.downloadFavorites(config)

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }
    }

    @Test
    fun downloadFavorites_validPayloadBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"version":1,"updatedAt":1700000000000,"favoriteOrder":[4,2,1]}""")
            )
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.downloadFavorites(config)

            assertTrue(result.isSuccess)
            val payload = result.getOrNull()
            assertNotNull(payload)
            assertEquals(listOf(4, 2, 1), payload?.favoriteOrder)
        }
    }

    @Test
    fun downloadNamePlans_404ReturnsNullBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.downloadNamePlans(config)

            assertTrue(result.isSuccess)
            assertNull(result.getOrNull())
        }
    }

    @Test
    fun downloadNamePlans_validPayloadBranch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """{
                        "version":1,
                        "updatedAt":1700000000000,
                        "surname":"Zhang",
                        "schemes":[{"id":10,"givenNameMode":"Double","gender":"Female","slot1":"A","slot2":"B"}]
                        }""".trimIndent()
                    )
            )
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.downloadNamePlans(config)

            assertTrue(result.isSuccess)
            val payload = result.getOrNull()
            assertNotNull(payload)
            assertEquals("Zhang", payload?.surname)
            assertEquals(1, payload?.schemes?.size)
            assertEquals(NamingGender.Female, payload?.schemes?.first()?.gender)
            assertEquals("A", payload?.schemes?.first()?.slot1)
            assertEquals("B", payload?.schemes?.first()?.slot2)
        }
    }

    @Test
    fun downloadNamePlans_missingGenderDefaultsToUnisex() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """{
                        "version":1,
                        "updatedAt":1700000000000,
                        "surname":"Zhang",
                        "schemes":[{"id":10,"givenNameMode":"Double","slot1":"A","slot2":"B"}]
                        }""".trimIndent()
                    )
            )
            val repository = WebDavRepository(client = OkHttpClient())
            val config = buildConfig(server)

            val result = repository.downloadNamePlans(config)

            assertTrue(result.isSuccess)
            val payload = result.getOrNull()
            assertNotNull(payload)
            assertEquals(1, payload?.schemes?.size)
            assertEquals(NamingGender.Unisex, payload?.schemes?.first()?.gender)
        }
    }

    private fun buildConfig(server: MockWebServer): WebDavConfig {
        return WebDavConfig(
            serverUrl = server.url("/webdav").toString().removeSuffix("/"),
            username = "test-user",
            password = "test-pass"
        )
    }
}
