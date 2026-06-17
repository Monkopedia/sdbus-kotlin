/**
 *
 * (C) 2024 - 2026 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * sdbus-kotlin. If not, see <https://www.gnu.org/licenses/>.
 */
package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MutablePropertyDelegate
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.SdbusException
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.mutableDelegate
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.propDelegate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

/**
 * Secret Service (`org.freedesktop.secrets`) round-trips against the python-dbusmock
 * independent peer (issue #34): a second, well-specified real-world D-Bus interface family
 * (after BlueZ) with a distinct type profile — `(oayays)` secret structs, `a{sv}` item
 * properties, `a{ss}` attributes, `ao` object trees and multi-out methods like
 * `OpenSession(sv → vo)`.
 *
 * The installed python-dbusmock (0.38.1) ships no `secrets` template, so the
 * org.freedesktop.Secret.* object tree is scripted onto a *generic* mock via the
 * `org.freedesktop.DBus.Mock` control interface (the same `AddMethod`/`AddObject` approach as
 * [DbusmockObjectManagerTest]) — see the mock-plumbing section at the bottom of this file. All
 * scripting calls use string/dict arguments only, so the mock can be driven from both backends.
 *
 * The client side goes through hand-written typed proxy classes mirroring the exact style the
 * `:codegen` module emits (PropertyDelegate-backed properties, suspend `callMethodAsync`
 * methods, `isGroupedReturn` for multi-out methods) — the way a real adopter would consume the
 * Secret Service API.
 *
 * Lives in commonTest so the exact same assertions run against BOTH the native sd-bus backend
 * (`linuxX64Test`) and the JVM dbus-java backend (`jvmTest`). Known JVM backend gaps are
 * gated, not re-tripped:
 * - issue #71 ([peerStructMarshallingSupported]): @Serializable structs cannot be marshalled
 *   to/from a real remote peer, which covers every `(oayays)` Secret transfer.
 * - issue #72 ([peerErrorNameMappingSupported]): foreign error names/messages are discarded
 *   (everything surfaces as AccessDenied), so error-name assertions are gated while the
 *   `assertFailsWith<SdbusException>` part always runs.
 * - issue #74 ([peerGroupedReturnSupported], FOUND BY THIS SUITE): multi-out method replies
 *   (`isGroupedReturn`, the shape codegen emits for 2+-out-arg methods) cannot be
 *   deserialized from a real remote peer, which covers `OpenSession` (vo), service-level
 *   `SearchItems` (aoao) and `Unlock`/`Lock` (aoo).
 *
 * Skips cleanly when python3-dbusmock is not installed (see [DbusmockHarness]).
 */
class DbusmockSecretServiceTest {

    // --- Session setup -----------------------------------------------------------------------

    @Test
    fun openSession_plainAlgorithm_createsClosableSessions() = withSecretService("Session") {
        if (!peerGroupedReturnSupported) {
            // KNOWN JVM BACKEND BUG (issue #74, found by this suite; see the
            // peerGroupedReturnSupported KDoc): OpenSession's (v output, o result) grouped
            // reply cannot be deserialized from a real remote peer, and every flow in this
            // test starts from it.
            println(
                "[DbusmockSecretServiceTest] SKIP OpenSession flows: known JVM backend " +
                    "grouped-return deserialization gap against remote peers (issue #74)."
            )
            return@withSecretService
        }
        val service = SecretServiceProxy(proxy)

        // OpenSession is a multi-out method (v output, o result) consumed as a grouped return.
        val first = service.openSession("plain", Variant(""))
        assertEquals("", first.output.get<String>(), "plain negotiation has an empty output")
        assertTrue(
            first.result.value.startsWith("/org/freedesktop/secrets/session/"),
            "unexpected session path ${first.result}"
        )

        val second = service.openSession("plain", Variant(""))
        assertNotEquals(first.result, second.result, "every OpenSession creates a fresh session")

        withProxyAt(second.result) { sessionProxy ->
            val session = SecretSessionProxy(sessionProxy)
            session.close()
            // The session object is gone from the peer once closed.
            assertFailsWith<SdbusException> { session.close() }
        }

        // Closing the second session does not affect the first.
        withProxyAt(first.result) { SecretSessionProxy(it).close() }
    }

    // --- Collection discovery ----------------------------------------------------------------

    @Test
    fun collections_discoveredThroughAliasAndProperties() = withSecretService(
        "Discovery",
        CollectionSpec(LOGIN_PATH, "Login", locked = false),
        CollectionSpec(VAULT_PATH, "Vault", locked = true)
    ) {
        val service = SecretServiceProxy(proxy)
        assertEquals(listOf(ObjectPath(LOGIN_PATH), ObjectPath(VAULT_PATH)), service.collections)

        // The default collection is discovered through the ReadAlias("default") convention.
        assertEquals(ObjectPath(LOGIN_PATH), service.readAlias("default"))
        assertEquals(ObjectPath("/"), service.readAlias("no-such-alias"))

        withProxyAt(ObjectPath(LOGIN_PATH)) { collectionProxy ->
            val login = SecretCollectionProxy(collectionProxy)
            assertEquals("Login", login.label)
            assertFalse(login.locked)
            assertEquals(emptyList<ObjectPath>(), login.items)
            assertEquals(CREATED_STAMP, login.created)
            assertEquals(CREATED_STAMP, login.modified)
        }
        withProxyAt(ObjectPath(VAULT_PATH)) { collectionProxy ->
            val vault = SecretCollectionProxy(collectionProxy)
            assertEquals("Vault", vault.label)
            assertTrue(vault.locked)
        }
    }

    // --- CreateItem / GetSecret / GetSecrets struct round-trips ------------------------------

    @Test
    fun createItem_getSecret_roundTripsSecretStructs() = withSecretService(
        "Create",
        CollectionSpec(LOGIN_PATH, "Login", locked = false)
    ) {
        if (!peerStructMarshallingSupported || !peerGroupedReturnSupported) {
            // KNOWN JVM BACKEND BUGS (issues #71 + #74; see the flags' KDoc): the (oayays)
            // Secret struct cannot be sent to or decoded from a real remote peer (#71) —
            // the substance of this whole test — and the OpenSession/CreateItem grouped
            // replies it builds on cannot be deserialized either (#74).
            println(
                "[DbusmockSecretServiceTest] SKIP CreateItem/GetSecret/GetSecrets struct " +
                    "flows: known JVM backend struct marshalling (issue #71) and " +
                    "grouped-return (issue #74) gaps against remote peers."
            )
            return@withSecretService
        }

        val service = SecretServiceProxy(proxy)
        val session = service.openSession("plain", Variant("")).result

        withProxyAt(ObjectPath(LOGIN_PATH)) { collectionProxy ->
            val collection = SecretCollectionProxy(collectionProxy)
            val created = collection.createItem(
                mapOf(
                    "org.freedesktop.Secret.Item.Label" to Variant("Email password"),
                    "org.freedesktop.Secret.Item.Attributes" to
                        Variant(mapOf("service" to "mail", "user" to "alice"))
                ),
                Secret(session, listOf(1u, 2u, 3u), "hunter2 🔑".toSecretBytes(), "text/plain"),
                replace = false
            )
            assertEquals(ObjectPath("/"), created.prompt, "creation must not require a prompt")
            assertTrue(
                created.item.value.startsWith("$LOGIN_PATH/"),
                "item ${created.item} should live under its collection"
            )
            assertEquals(listOf(created.item), collection.items)

            installItemMethods(created.item)
            withProxyAt(created.item) { itemProxy ->
                val item = SecretItemProxy(itemProxy)
                assertEquals("Email password", item.label)
                assertEquals(mapOf("service" to "mail", "user" to "alice"), item.attributes)
                assertFalse(item.locked)

                // The peer reconstructs the secret for the *requesting* session.
                val secret = item.getSecret(session)
                assertEquals(session, secret.session)
                assertEquals(listOf<UByte>(1u, 2u, 3u), secret.parameters)
                assertEquals("hunter2 🔑", secret.value.asSecretText())
                assertEquals("text/plain", secret.contentType)

                // SetSecret replaces the stored secret in place.
                item.setSecret(Secret(session, emptyList(), "rotated".toSecretBytes(), "x/y"))
                val rotated = item.getSecret(session)
                assertEquals("rotated", rotated.value.asSecretText())
                assertEquals("x/y", rotated.contentType)
            }

            // GetSecrets batches multiple items in one a{o(oayays)} reply.
            val other =
                seedItem(LOGIN_PATH, "Other", mapOf("service" to "web"), "s3cret", "text/plain")
            val secrets = service.getSecrets(listOf(created.item, other), session)
            assertEquals(setOf(created.item, other), secrets.keys)
            assertEquals("rotated", secrets.getValue(created.item).value.asSecretText())
            assertEquals("s3cret", secrets.getValue(other).value.asSecretText())
            assertEquals(session, secrets.getValue(other).session)
        }
    }

    // --- SearchItems + item properties --------------------------------------------------------

    @Test
    fun searchItems_andItemProperties_acrossCollections() = withSecretService(
        "Search",
        CollectionSpec(LOGIN_PATH, "Login", locked = false),
        CollectionSpec(VAULT_PATH, "Vault", locked = true)
    ) {
        val service = SecretServiceProxy(proxy)
        val mailAttrs = mapOf("service" to "mail", "user" to "alice")
        val mailItem = seedItem(LOGIN_PATH, "Mail password", mailAttrs, "hunter2", "text/plain")
        val webItem = seedItem(
            LOGIN_PATH,
            "Web password",
            mapOf("service" to "web", "user" to "alice"),
            "s3cret",
            "text/plain"
        )
        val vaultItem = seedItem(VAULT_PATH, "Vault mail", mailAttrs, "locked-away", "text/plain")

        // Item properties through the org.freedesktop.DBus.Properties interface.
        withProxyAt(mailItem) { itemProxy ->
            val item = SecretItemProxy(itemProxy)
            assertEquals("Mail password", item.label)
            assertEquals(mailAttrs, item.attributes)
            assertFalse(item.locked)

            // Label is writable; the write goes through Properties.Set on the foreign peer.
            item.label = "Renamed mail password"
            assertEquals("Renamed mail password", item.label)
        }
        withProxyAt(vaultItem) { itemProxy ->
            assertTrue(
                SecretItemProxy(itemProxy).locked,
                "items in a locked collection are locked"
            )
        }

        // Collection-level search (single ao reply) is scoped to the collection's own items.
        withProxyAt(ObjectPath(LOGIN_PATH)) { collectionProxy ->
            val login = SecretCollectionProxy(collectionProxy)
            assertEquals(listOf(mailItem, webItem), login.items)
            assertEquals(listOf(mailItem), login.searchItems(mapOf("service" to "mail")))
            assertEquals(listOf(mailItem, webItem), login.searchItems(emptyMap()))
            assertEquals(emptyList<ObjectPath>(), login.searchItems(mapOf("user" to "bob")))
        }

        if (!peerGroupedReturnSupported) {
            // KNOWN JVM BACKEND BUG (issue #74, found by this suite; see the
            // peerGroupedReturnSupported KDoc): the service-level SearchItems reply
            // (ao unlocked, ao locked) cannot be deserialized from a real remote peer.
            println(
                "[DbusmockSecretServiceTest] SKIP service-level SearchItems sub-cases: " +
                    "known JVM backend grouped-return gap against remote peers (issue #74)."
            )
            return@withSecretService
        }

        // Service-level search splits matches into (unlocked, locked).
        val alice = service.searchItems(mapOf("user" to "alice"))
        assertEquals(listOf(mailItem, webItem), alice.unlocked)
        assertEquals(listOf(vaultItem), alice.locked)

        val mail = service.searchItems(mailAttrs)
        assertEquals(listOf(mailItem), mail.unlocked)
        assertEquals(listOf(vaultItem), mail.locked)

        val none = service.searchItems(mapOf("user" to "bob"))
        assertTrue(none.unlocked.isEmpty(), "no items expected for user=bob")
        assertTrue(none.locked.isEmpty(), "no locked items expected for user=bob")
    }

    // --- ItemCreated / ItemDeleted signals -----------------------------------------------------

    @Test
    fun itemCreatedAndDeleted_signalsTrackItemLifecycle() = withSecretService(
        "Signals",
        CollectionSpec(LOGIN_PATH, "Login", locked = false)
    ) {
        withProxyAt(ObjectPath(LOGIN_PATH)) { collectionProxy ->
            val createdSignal = CompletableDeferred<ObjectPath>()
            val deletedSignal = CompletableDeferred<ObjectPath>()
            val registrations = listOf(
                collectionProxy.onSignal(
                    SecretCollectionProxy.INTERFACE_NAME,
                    SignalName("ItemCreated")
                ) {
                    call { path: ObjectPath -> createdSignal.complete(path) }
                },
                collectionProxy.onSignal(
                    SecretCollectionProxy.INTERFACE_NAME,
                    SignalName("ItemDeleted")
                ) {
                    call { path: ObjectPath -> deletedSignal.complete(path) }
                }
            )

            try {
                val itemPath = seedItem(
                    LOGIN_PATH,
                    "Short-lived",
                    mapOf("service" to "tmp"),
                    "fleeting",
                    "text/plain"
                )
                assertEquals(
                    itemPath,
                    withTimeout(10_000) { createdSignal.await() },
                    "ItemCreated should carry the new item's path"
                )
                val collection = SecretCollectionProxy(collectionProxy)
                assertEquals(listOf(itemPath), collection.items)

                installItemMethods(itemPath)
                withProxyAt(itemPath) { itemProxy ->
                    assertEquals(ObjectPath("/"), SecretItemProxy(itemProxy).delete())
                }
                assertEquals(
                    itemPath,
                    withTimeout(10_000) { deletedSignal.await() },
                    "ItemDeleted should carry the deleted item's path"
                )
                assertEquals(emptyList<ObjectPath>(), collection.items)

                // The item object really is gone from the peer afterwards. (Only the failure
                // itself is asserted: the error *name* for a vanished object is produced by
                // the foreign stack and is implementation-defined.)
                withProxyAt(itemPath) { itemProxy ->
                    assertFailsWith<SdbusException> {
                        SecretItemProxy(itemProxy).getSecret(ObjectPath("/"))
                    }
                }
            } finally {
                registrations.forEach { it.release() }
            }
        }
    }

    // --- Locked collections: error paths + Unlock/Lock ----------------------------------------

    @Test
    fun lockedCollection_rejectsAccess_untilUnlocked() = withSecretService(
        "Locking",
        CollectionSpec(LOGIN_PATH, "Login", locked = false),
        CollectionSpec(VAULT_PATH, "Vault", locked = true)
    ) {
        val service = SecretServiceProxy(proxy)
        // The locked-error paths don't dereference the session, so a placeholder keeps them
        // running where OpenSession's grouped reply cannot be consumed (issue #74, JVM).
        val session = if (peerGroupedReturnSupported) {
            service.openSession("plain", Variant("")).result
        } else {
            ObjectPath("/")
        }
        val vaultAttrs = mapOf("realm" to "production")
        val vaultItem = seedItem(VAULT_PATH, "Vault secret", vaultAttrs, "top-secret", "text/x")
        installItemMethods(vaultItem)

        withProxyAt(ObjectPath(VAULT_PATH)) { collectionProxy ->
            val vault = SecretCollectionProxy(collectionProxy)
            assertTrue(vault.locked)

            // Reading a secret of an item in a locked collection is rejected. (The error
            // happens before any secret struct is marshalled, so this runs on both backends.)
            withProxyAt(vaultItem) { itemProxy ->
                val error =
                    assertFailsWith<SdbusException> {
                        SecretItemProxy(itemProxy).getSecret(session)
                    }
                assertSecretError(error, "org.freedesktop.Secret.Error.IsLocked")
            }

            if (peerStructMarshallingSupported) {
                // Creating an item in a locked collection is rejected too.
                val error = assertFailsWith<SdbusException> {
                    vault.createItem(
                        mapOf("org.freedesktop.Secret.Item.Label" to Variant("Denied")),
                        Secret(session, emptyList(), "nope".toSecretBytes(), "text/plain"),
                        replace = false
                    )
                }
                assertSecretError(error, "org.freedesktop.Secret.Error.IsLocked")
            } else {
                // KNOWN JVM BACKEND BUG (issue #71): the CreateItem call itself cannot be
                // sent, because its (oayays) secret argument is a struct.
                println(
                    "[DbusmockSecretServiceTest] SKIP locked-CreateItem error sub-case: " +
                        "known JVM backend struct marshalling gap (issue #71)."
                )
            }

            if (!peerGroupedReturnSupported) {
                // KNOWN JVM BACKEND BUG (issue #74, found by this suite; see the
                // peerGroupedReturnSupported KDoc): the Unlock/Lock (ao, o) and service
                // SearchItems (ao, ao) grouped replies driving the rest of this flow cannot
                // be deserialized from a real remote peer.
                println(
                    "[DbusmockSecretServiceTest] SKIP Unlock/Lock round-trip sub-cases: " +
                        "known JVM backend grouped-return gap against remote peers " +
                        "(issue #74)."
                )
                return@withProxyAt
            }

            // Searches report the item on the locked side while the collection is locked.
            assertEquals(listOf(vaultItem), service.searchItems(vaultAttrs).locked)

            // Unlock flips the collection and its items, with PropertiesChanged on the peer.
            val unlocked = service.unlock(listOf(ObjectPath(VAULT_PATH)))
            assertEquals(listOf(ObjectPath(VAULT_PATH)), unlocked.objects)
            assertEquals(ObjectPath("/"), unlocked.prompt, "no prompt expected from Unlock")
            assertFalse(vault.locked)

            val search = service.searchItems(vaultAttrs)
            assertEquals(listOf(vaultItem), search.unlocked)
            assertTrue(search.locked.isEmpty(), "nothing should stay locked after Unlock")

            if (peerStructMarshallingSupported) {
                withProxyAt(vaultItem) { itemProxy ->
                    val secret = SecretItemProxy(itemProxy).getSecret(session)
                    assertEquals("top-secret", secret.value.asSecretText())
                    assertEquals("text/x", secret.contentType)
                }
            }

            // Lock is symmetric.
            val locked = service.lock(listOf(ObjectPath(VAULT_PATH)))
            assertEquals(listOf(ObjectPath(VAULT_PATH)), locked.objects)
            assertTrue(vault.locked)
            withProxyAt(vaultItem) { itemProxy ->
                val error =
                    assertFailsWith<SdbusException> {
                        SecretItemProxy(itemProxy).getSecret(session)
                    }
                assertSecretError(error, "org.freedesktop.Secret.Error.IsLocked")
            }
        }
    }

    /**
     * Asserts the D-Bus error name produced by the mocked Secret Service — gated on
     * [peerErrorNameMappingSupported] (issue #72), where the JVM backend is known to discard
     * foreign error names. The fact that an [SdbusException] is thrown at all is asserted
     * unconditionally at each call site via `assertFailsWith`.
     */
    private fun assertSecretError(error: SdbusException, expectedName: String) {
        if (!peerErrorNameMappingSupported) {
            // KNOWN JVM BACKEND BUG (issue #72; see peerErrorNameMappingSupported KDoc).
            println(
                "[DbusmockSecretServiceTest] SKIP error-name assertion for $expectedName: " +
                    "known JVM backend gap (issue #72). Actual name surfaced: ${error.name}"
            )
            return
        }
        assertEquals(expectedName, error.name, "Secret Service error name was not preserved")
    }
}

// --- Typed proxies (hand-written in the exact style the :codegen module emits) ---------------

/** The Secret Service `(oayays)` secret struct. */
@Serializable
private data class Secret(
    val session: ObjectPath,
    val parameters: List<UByte>,
    val value: List<UByte>,
    val contentType: String
)

/** Grouped `(v output, o result)` reply of `Service.OpenSession`. */
@Serializable
private data class OpenSessionResult(val output: Variant, val result: ObjectPath)

/** Grouped `(o item, o prompt)` reply of `Collection.CreateItem`. */
@Serializable
private data class CreateItemResult(val item: ObjectPath, val prompt: ObjectPath)

/** Grouped `(ao unlocked, ao locked)` reply of `Service.SearchItems`. */
@Serializable
private data class SearchItemsResult(val unlocked: List<ObjectPath>, val locked: List<ObjectPath>)

/** Grouped `(ao objects, o prompt)` reply of `Service.Unlock` / `Service.Lock`. */
@Serializable
private data class ObjectsAndPrompt(val objects: List<ObjectPath>, val prompt: ObjectPath)

private class SecretServiceProxy(val proxy: Proxy) {
    val collectionsProperty: PropertyDelegate<SecretServiceProxy, List<ObjectPath>> =
        proxy.propDelegate(INTERFACE_NAME, PropertyName("Collections"))

    val collections: List<ObjectPath> by collectionsProperty

    suspend fun openSession(algorithm: String, input: Variant): OpenSessionResult =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("OpenSession")) {
            isGroupedReturn = true
            call(algorithm, input)
        }

    suspend fun readAlias(name: String): ObjectPath =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("ReadAlias")) {
            call(name)
        }

    suspend fun searchItems(attributes: Map<String, String>): SearchItemsResult =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("SearchItems")) {
            isGroupedReturn = true
            call(attributes)
        }

    suspend fun unlock(objects: List<ObjectPath>): ObjectsAndPrompt =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("Unlock")) {
            isGroupedReturn = true
            call(objects)
        }

    suspend fun lock(objects: List<ObjectPath>): ObjectsAndPrompt =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("Lock")) {
            isGroupedReturn = true
            call(objects)
        }

    suspend fun getSecrets(items: List<ObjectPath>, session: ObjectPath): Map<ObjectPath, Secret> =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("GetSecrets")) {
            call(items, session)
        }

    companion object {
        val INTERFACE_NAME = InterfaceName("org.freedesktop.Secret.Service")
    }
}

private class SecretCollectionProxy(val proxy: Proxy) {
    val itemsProperty: PropertyDelegate<SecretCollectionProxy, List<ObjectPath>> =
        proxy.propDelegate(INTERFACE_NAME, PropertyName("Items"))

    val items: List<ObjectPath> by itemsProperty

    val labelProperty: PropertyDelegate<SecretCollectionProxy, String> =
        proxy.propDelegate(INTERFACE_NAME, PropertyName("Label"))

    val label: String by labelProperty

    val lockedProperty: PropertyDelegate<SecretCollectionProxy, Boolean> =
        proxy.propDelegate(INTERFACE_NAME, PropertyName("Locked"))

    val locked: Boolean by lockedProperty

    val createdProperty: PropertyDelegate<SecretCollectionProxy, ULong> =
        proxy.propDelegate(INTERFACE_NAME, PropertyName("Created"))

    val created: ULong by createdProperty

    val modifiedProperty: PropertyDelegate<SecretCollectionProxy, ULong> =
        proxy.propDelegate(INTERFACE_NAME, PropertyName("Modified"))

    val modified: ULong by modifiedProperty

    suspend fun createItem(
        properties: Map<String, Variant>,
        secret: Secret,
        replace: Boolean
    ): CreateItemResult = proxy.callMethodAsync(INTERFACE_NAME, MethodName("CreateItem")) {
        isGroupedReturn = true
        call(properties, secret, replace)
    }

    suspend fun searchItems(attributes: Map<String, String>): List<ObjectPath> =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("SearchItems")) {
            call(attributes)
        }

    companion object {
        val INTERFACE_NAME = InterfaceName("org.freedesktop.Secret.Collection")
    }
}

private class SecretItemProxy(val proxy: Proxy) {
    val labelProperty: MutablePropertyDelegate<SecretItemProxy, String> =
        proxy.mutableDelegate(INTERFACE_NAME, PropertyName("Label"))

    var label: String by labelProperty

    val attributesProperty: PropertyDelegate<SecretItemProxy, Map<String, String>> =
        proxy.propDelegate(INTERFACE_NAME, PropertyName("Attributes"))

    val attributes: Map<String, String> by attributesProperty

    val lockedProperty: PropertyDelegate<SecretItemProxy, Boolean> =
        proxy.propDelegate(INTERFACE_NAME, PropertyName("Locked"))

    val locked: Boolean by lockedProperty

    suspend fun getSecret(session: ObjectPath): Secret =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("GetSecret")) {
            call(session)
        }

    suspend fun setSecret(secret: Secret): Unit =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("SetSecret")) {
            call(secret)
        }

    suspend fun delete(): ObjectPath =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("Delete")) {}

    companion object {
        val INTERFACE_NAME = InterfaceName("org.freedesktop.Secret.Item")
    }
}

private class SecretSessionProxy(val proxy: Proxy) {
    suspend fun close(): Unit = proxy.callMethodAsync(INTERFACE_NAME, MethodName("Close")) {}

    companion object {
        val INTERFACE_NAME = InterfaceName("org.freedesktop.Secret.Session")
    }
}

// --- Test fixture ------------------------------------------------------------------------------

private const val LOGIN_PATH = "/org/freedesktop/secrets/collection/login"
private const val VAULT_PATH = "/org/freedesktop/secrets/collection/vault"

/** `Created`/`Modified` timestamp the mock stamps onto collections. */
private val CREATED_STAMP = 1715000000uL

/** A collection to seed onto the mocked service. The first one is the `default` alias. */
private data class CollectionSpec(val path: String, val label: String, val locked: Boolean)

/**
 * [withDbusmockPeer] at the realistic `/org/freedesktop/secrets` root path with main interface
 * `org.freedesktop.Secret.Service` (the bus name stays unique per test), scripted into a Secret
 * Service shape with the given [collections] before [block] runs.
 */
private fun withSecretService(
    suffix: String,
    vararg collections: CollectionSpec,
    block: suspend DbusmockPeer.() -> Unit
) = withDbusmockPeer(
    "Secret$suffix",
    mockPath = "/org/freedesktop/secrets",
    mockInterface = SecretServiceProxy.INTERFACE_NAME.value
) {
    setupSecretService(collections.toList())
    block()
}

/** Runs [block] against a short-lived proxy for the peer's object at [path]. */
private suspend fun <T> DbusmockPeer.withProxyAt(path: ObjectPath, block: suspend (Proxy) -> T): T {
    val proxyAt = createProxy(connection, busName, path)
    return try {
        block(proxyAt)
    } finally {
        proxyAt.release()
    }
}

// --- Mock plumbing (org.freedesktop.DBus.Mock scripting) ---------------------------------------
//
// Everything below drives the dbusmock control interface with string/dict arguments only, so
// the very same setup runs on the JVM backend (which cannot send struct arguments, issue #71).
// Object creation happens *inside* scripted python (self.AddObject) following the pattern
// established in DbusmockObjectManagerTest.
//
// NOTE on the python snippets: dbusmock executes them via exec() with a separate locals dict,
// which gives them class-body scoping — comprehensions/generator expressions there cannot see
// the snippet's own variables, so the snippets deliberately use plain for-loops.

/** Scripts the service-level methods and seeds [collections] onto the mock. */
private suspend fun DbusmockPeer.setupSecretService(collections: List<CollectionSpec>) {
    val defaultPath = collections.firstOrNull()?.path ?: "/"
    addMethod("OpenSession", "sv", "vo", OPEN_SESSION_CODE)
    addMethod("ReadAlias", "s", "o", readAliasCode(defaultPath))
    addMethod("SearchItems", "a{ss}", "aoao", SERVICE_SEARCH_ITEMS_CODE)
    addMethod("Unlock", "ao", "aoo", setLockedCode(false))
    addMethod("Lock", "ao", "aoo", setLockedCode(true))
    addMethod("GetSecrets", "aoo", "a{o(oayays)}", GET_SECRETS_CODE)
    // Mock-plumbing helper (not part of the Secret Service API): creates a collection object.
    addMethod("AddCollection", "ssb", "", ADD_COLLECTION_CODE)
    addProperty("Collections", Variant(collections.map { ObjectPath(it.path) }))

    for (spec in collections) {
        proxy.callMethod<Unit>(iface, MethodName("AddCollection")) {
            call(spec.path, spec.label, spec.locked)
        }
        withProxyAt(ObjectPath(spec.path)) { collectionControl ->
            // SeedItem is mock plumbing: it creates a fully-formed item from primitive
            // arguments so item-dependent tests can run on the JVM backend too (which cannot
            // send the (oayays) secret struct that the real CreateItem takes, issue #71).
            collectionControl.addMockMethod("SeedItem", "sa{ss}ays", "o", SEED_ITEM_CODE)
            collectionControl.addMockMethod("CreateItem", "a{sv}(oayays)b", "oo", CREATE_ITEM_CODE)
            collectionControl.addMockMethod("SearchItems", "a{ss}", "ao", COLLECTION_SEARCH_CODE)
        }
    }
}

/**
 * Adds a scripted method on this mock object's *main* interface (dbusmock interprets an empty
 * interface name as the object's primary interface).
 */
private fun Proxy.addMockMethod(name: String, inSig: String, outSig: String, code: String) {
    callMethod<Unit>(MOCK_INTERFACE, MethodName("AddMethod")) {
        call("", name, inSig, outSig, code)
    }
}

/**
 * Seeds a ready-made item (with stored secret) into the collection at [collectionPath] through
 * the mock-plumbing `SeedItem` call, returning the new item's path. Emits `ItemCreated` and
 * updates the collection's `Items` property exactly like the real `CreateItem` (which routes
 * through the same scripted code).
 */
private suspend fun DbusmockPeer.seedItem(
    collectionPath: String,
    label: String,
    attributes: Map<String, String>,
    secret: String,
    contentType: String
): ObjectPath = withProxyAt(ObjectPath(collectionPath)) { collectionProxy ->
    collectionProxy.callMethodAsync(
        SecretCollectionProxy.INTERFACE_NAME,
        MethodName("SeedItem")
    ) {
        call(label, attributes, secret.toSecretBytes(), contentType)
    }
}

/**
 * Installs the scripted `org.freedesktop.Secret.Item` methods on the item at [itemPath].
 * Items are AddObject-ed by scripted python without methods; installing them from Kotlin (all
 * string arguments) keeps the scripting JVM-compatible and the python snippets un-nested.
 */
private suspend fun DbusmockPeer.installItemMethods(itemPath: ObjectPath) {
    withProxyAt(itemPath) { itemControl ->
        itemControl.addMockMethod("GetSecret", "o", "(oayays)", ITEM_GET_SECRET_CODE)
        itemControl.addMockMethod("SetSecret", "(oayays)", "", ITEM_SET_SECRET_CODE)
        itemControl.addMockMethod("Delete", "", "o", ITEM_DELETE_CODE)
    }
}

private fun String.toSecretBytes(): List<UByte> = encodeToByteArray().map { it.toUByte() }

private fun List<UByte>.asSecretText(): String = map { it.toByte() }.toByteArray().decodeToString()

// --- Python snippets executed by the dbusmock peer ----------------------------------------------

/** `OpenSession(s algorithm, v input) -> (v output, o result)`: plain sessions only. */
private val OPEN_SESSION_CODE = """
    n = getattr(self, 'session_count', 0) + 1
    self.session_count = n
    path = dbus.ObjectPath('/org/freedesktop/secrets/session/%d' % n)
    self.AddObject(path, 'org.freedesktop.Secret.Session', {},
                   [('Close', '', '', 'self.RemoveObject(str(self.path))')])
    ret = (dbus.String('', variant_level=1), path)
""".trimIndent()

/** `ReadAlias(s name) -> o`: only the `default` alias resolves, to the first collection. */
private fun readAliasCode(defaultPath: String) = """
    aliases = {'default': dbus.ObjectPath('$defaultPath')}
    ret = aliases.get(args[0], dbus.ObjectPath('/'))
""".trimIndent()

/** `Service.SearchItems(a{ss}) -> (ao unlocked, ao locked)` across all collections. */
private val SERVICE_SEARCH_ITEMS_CODE = """
    unlocked = dbus.Array([], signature='o')
    locked = dbus.Array([], signature='o')
    for path in sorted(objects.keys()):
        props = objects[path].props.get('org.freedesktop.Secret.Item')
        if props is None:
            continue
        attrs = props.get('Attributes', {})
        match = True
        for k in args[0].keys():
            if attrs.get(k) != args[0][k]:
                match = False
        if not match:
            continue
        if props.get('Locked', False):
            locked.append(dbus.ObjectPath(path))
        else:
            unlocked.append(dbus.ObjectPath(path))
    ret = (unlocked, locked)
""".trimIndent()

/**
 * `Unlock(ao) -> (ao unlocked, o prompt)` / `Lock(ao) -> (ao locked, o prompt)`: flips the
 * `Locked` property of each target collection *and* the items underneath it (real services
 * lock/unlock whole collections), emitting `PropertiesChanged` for every flip.
 */
private fun setLockedCode(locked: Boolean): String {
    val py = if (locked) "True" else "False"
    return """
        done = dbus.Array([], signature='o')
        for target in args[0]:
            if str(target) not in objects:
                continue
            prefix = str(target) + '/'
            for path in list(objects.keys()):
                if path != str(target) and not path.startswith(prefix):
                    continue
                obj = objects[path]
                for iface in ('org.freedesktop.Secret.Collection',
                              'org.freedesktop.Secret.Item'):
                    if iface in obj.props and bool(obj.props[iface].get('Locked', False)) != $py:
                        obj.UpdateProperties(iface, {'Locked': dbus.Boolean($py)})
            done.append(dbus.ObjectPath(str(target)))
        ret = (done, dbus.ObjectPath('/'))
    """.trimIndent()
}

/** `GetSecrets(ao items, o session) -> a{o(oayays)}`, re-targeted at the requesting session. */
private val GET_SECRETS_CODE = """
    ret = dbus.Dictionary({}, signature='o(oayays)')
    for p in args[0]:
        s = objects[str(p)].secret
        secret = dbus.Struct(
            (dbus.ObjectPath(args[1]), s[1], s[2], s[3]), signature='oayays')
        ret[dbus.ObjectPath(str(p))] = secret
""".trimIndent()

/** Mock plumbing: `AddCollection(s path, s label, b locked)` creates a collection object. */
private val ADD_COLLECTION_CODE = """
    self.AddObject(args[0], 'org.freedesktop.Secret.Collection', {
        'Items': dbus.Array([], signature='o'),
        'Label': dbus.String(args[1]),
        'Locked': dbus.Boolean(args[2]),
        'Created': dbus.UInt64(1715000000),
        'Modified': dbus.UInt64(1715000000),
    }, [])
""".trimIndent()

/**
 * Mock plumbing on collections: `SeedItem(s label, a{ss} attributes, ay value, s contentType)
 * -> o`. Creates the item object (inheriting the collection's `Locked` state), stores its
 * secret, appends it to `Items` (emitting `PropertiesChanged`) and emits `ItemCreated`.
 */
private val SEED_ITEM_CODE = """
    cprops = self.props['org.freedesktop.Secret.Collection']
    n = getattr(self, 'item_count', 0) + 1
    self.item_count = n
    path = dbus.ObjectPath('%s/%d' % (self.path, n))
    self.AddObject(path, 'org.freedesktop.Secret.Item', {
        'Label': dbus.String(args[0]),
        'Attributes': dbus.Dictionary(args[1], signature='ss'),
        'Locked': dbus.Boolean(bool(cprops['Locked'])),
        'Created': dbus.UInt64(1715000001),
        'Modified': dbus.UInt64(1715000001),
    }, [])
    objects[str(path)].secret = dbus.Struct(
        (dbus.ObjectPath('/'), dbus.Array([], signature='y'),
         dbus.Array(args[2], signature='y'), dbus.String(args[3])),
        signature='oayays')
    self.UpdateProperties('org.freedesktop.Secret.Collection',
                          {'Items': dbus.Array(list(cprops['Items']) + [path], signature='o')})
    self.EmitSignal('org.freedesktop.Secret.Collection', 'ItemCreated', 'o', [path])
    ret = path
""".trimIndent()

/**
 * `Collection.CreateItem(a{sv} properties, (oayays) secret, b replace) -> (o item, o prompt)`.
 * Rejects locked collections with `org.freedesktop.Secret.Error.IsLocked`, then routes through
 * the scripted `SeedItem` and replaces the stored secret with the caller's verbatim struct.
 * (The `replace` flag is accepted but not implemented — no test relies on it.)
 */
private val CREATE_ITEM_CODE = """
    if self.props['org.freedesktop.Secret.Collection']['Locked']:
        raise dbus.exceptions.DBusException(
            'Cannot create an item in a locked collection',
            name='org.freedesktop.Secret.Error.IsLocked')
    iprops = args[0]
    attrs = dbus.Dictionary(
        iprops.get('org.freedesktop.Secret.Item.Attributes', {}), signature='ss')
    label = str(iprops.get('org.freedesktop.Secret.Item.Label', ''))
    path = self.SeedItem(label, attrs, args[1][2], args[1][3])
    objects[str(path)].secret = args[1]
    ret = (dbus.ObjectPath(str(path)), dbus.ObjectPath('/'))
""".trimIndent()

/** `Collection.SearchItems(a{ss}) -> ao`, scoped to this collection's `Items`. */
private val COLLECTION_SEARCH_CODE = """
    ret = dbus.Array([], signature='o')
    for p in self.props['org.freedesktop.Secret.Collection']['Items']:
        attrs = objects[str(p)].props['org.freedesktop.Secret.Item'].get('Attributes', {})
        match = True
        for k in args[0].keys():
            if attrs.get(k) != args[0][k]:
                match = False
        if match:
            ret.append(dbus.ObjectPath(str(p)))
""".trimIndent()

/** `Item.GetSecret(o session) -> (oayays)`, re-targeted at the requesting session. */
private val ITEM_GET_SECRET_CODE = """
    if self.props['org.freedesktop.Secret.Item']['Locked']:
        raise dbus.exceptions.DBusException(
            'Cannot get secret of a locked object',
            name='org.freedesktop.Secret.Error.IsLocked')
    s = self.secret
    ret = dbus.Struct(
        (dbus.ObjectPath(args[0]), s[1], s[2], s[3]), signature='oayays')
""".trimIndent()

/** `Item.SetSecret((oayays) secret)`. */
private val ITEM_SET_SECRET_CODE = """
    if self.props['org.freedesktop.Secret.Item']['Locked']:
        raise dbus.exceptions.DBusException(
            'Cannot set secret of a locked object',
            name='org.freedesktop.Secret.Error.IsLocked')
    self.secret = args[0]
    self.UpdateProperties('org.freedesktop.Secret.Item', {'Modified': dbus.UInt64(1715000002)})
""".trimIndent()

/**
 * `Item.Delete() -> o prompt`: detaches the item from its parent collection's `Items`
 * (emitting `PropertiesChanged`), emits `ItemDeleted` from the collection, and removes the
 * item object from the bus.
 */
private val ITEM_DELETE_CODE = """
    parent = objects[str(self.path).rsplit('/', 1)[0]]
    items = dbus.Array([], signature='o')
    for p in parent.props['org.freedesktop.Secret.Collection']['Items']:
        if str(p) != str(self.path):
            items.append(p)
    parent.UpdateProperties('org.freedesktop.Secret.Collection', {'Items': items})
    parent.EmitSignal(
        'org.freedesktop.Secret.Collection', 'ItemDeleted', 'o',
        [dbus.ObjectPath(str(self.path))])
    parent.RemoveObject(str(self.path))
    ret = dbus.ObjectPath('/')
""".trimIndent()

/**
 * Whether this backend can deserialize multi-out (grouped, `isGroupedReturn = true`) method
 * replies received from a real remote (out-of-process) peer.
 *
 * `true` on the native sd-bus backend. `false` on the JVM backend: `Message.deserialize` on
 * JVM consumes exactly ONE payload value and validates it against the *degrouped tuple*
 * signature of all out-args, so e.g. `OpenSession`'s `(v, o)` reply fails with "signature
 * mismatch expected=(vo) actual=vs" and the service-level `SearchItems`' `(ao, ao)` reply
 * with "expected=(aoao) actual=ao" — the remaining reply arguments are never consumed. This
 * breaks every codegen-generated proxy method for a D-Bus method with 2+ out-args against a
 * real service. Own-server tests miss it because in-process calls short-circuit through
 * JvmStaticDispatch, bypassing wire deserialization entirely.
 *
 * Found by this suite (issue #34); the fix is ticketed as issue #74 — when it lands, flip
 * the JVM actual to `true` so the full assertions enforce parity.
 */
internal expect val peerGroupedReturnSupported: Boolean
