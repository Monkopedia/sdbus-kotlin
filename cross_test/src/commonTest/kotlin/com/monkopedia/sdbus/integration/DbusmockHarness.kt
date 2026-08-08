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

/**
 * Cross-platform (JVM + native) test harness that drives our sdbus-kotlin proxies against
 * [python-dbusmock](https://github.com/martinpitt/python-dbusmock) — an *independent*
 * Python/GDBus implementation of the D-Bus service side. Running our client against it
 * validates our marshalling/wire protocol against a foreign stack, catching symmetric
 * serializer bugs that our own-server-vs-our-proxy round-trips structurally cannot.
 *
 * ## How dbusmock is invoked
 *
 * dbusmock runs as `python3 -m dbusmock <NAME> <PATH> <INTERFACE>`, claiming a bus name and
 * exposing a *generic* mock object. The object's behaviour is scripted at runtime over the
 * `org.freedesktop.DBus.Mock` control interface (`AddMethod`, `AddProperty`, …). The harness
 * launches it on the *current* bus (the private session bus created by `dbus-run-session`),
 * so [com.monkopedia.sdbus.createBusConnection] connects our client to the very same bus.
 *
 * ## Installing python-dbusmock
 *
 * - Debian/Ubuntu CI: `apt-get install -y python3-dbusmock`
 * - Arch: `pacman -S python-dbusmock` (or via pip into a venv)
 * - pip: `python3 -m venv --system-site-packages venv && venv/bin/pip install python-dbusmock`
 *   (dbusmock needs `dbus-python` + `PyGObject`, which is why a `--system-site-packages` venv
 *   or distro package is the smoothest path).
 *
 * If a custom interpreter is needed (e.g. a venv), point the `DBUSMOCK_PYTHON` environment
 * variable at it; otherwise `python3` on `PATH` is used.
 *
 * ## Graceful skip, and `DBUSMOCK_REQUIRED`
 *
 * If python3 / python3-dbusmock is not present, or no D-Bus session bus is reachable,
 * [launchDbusmock] returns `null` and the caller SKIPs (see [skipTest]) rather than failing —
 * so a contributor laptop without dbusmock still passes.
 *
 * That default is wrong for a CI job that installs python3-dbusmock deliberately: there, a
 * vanished package would silently turn the whole foreign-peer layer into no-ops while the build
 * stayed green. Setting the [DBUSMOCK_REQUIRED_ENV] environment variable inverts it — every
 * skip path fails instead, naming the underlying reason.
 */
internal expect fun dbusmockGetenv(name: String): String?

/**
 * Environment variable that turns a "dbusmock could not be started" skip into a hard failure.
 * Set by the CI job that installs python3-dbusmock on purpose.
 */
internal const val DBUSMOCK_REQUIRED_ENV = "DBUSMOCK_REQUIRED"

/** Message reported by every suite that skips because no dbusmock peer could be started. */
internal const val DBUSMOCK_UNAVAILABLE_SKIP =
    "python-dbusmock unavailable. Install via 'apt install python3-dbusmock' / " +
        "'pip install python-dbusmock' (see DbusmockHarness KDoc), or set " +
        "$DBUSMOCK_REQUIRED_ENV=1 to fail instead of skipping."

/**
 * Handles a dbusmock peer that could not be started, [reason] saying why (no session bus, python
 * missing, module missing, …).
 *
 * Returns `null` — the caller then skips — unless [DBUSMOCK_REQUIRED_ENV] is set, in which case
 * it fails loudly with [reason] instead.
 */
internal fun dbusmockUnavailable(reason: String): DbusmockHandle? {
    if (dbusmockGetenv(DBUSMOCK_REQUIRED_ENV) == null) return null
    error("$DBUSMOCK_REQUIRED_ENV is set but no dbusmock peer could be started: $reason")
}

/**
 * Reports the running test as skipped with [reason], so the test report says "skipped" instead of
 * claiming a pass for a case that asserted nothing.
 *
 * On the JVM this raises a JUnit assumption failure, which Gradle records as `skipped`. Kotlin/
 * Native's test runner has no runtime-skip primitive — an early return is indistinguishable from a
 * pass, and a TeamCity `testIgnored` emitted from inside a running test is rejected by the Gradle
 * parser — so the native actual can only write [reason] to the captured test output. Because a
 * printed reason does not stop the report claiming a pass (#186), `:cross_test:linuxX64Test` does not
 * run these suites at all when the harness cannot start: see the exclusion in
 * `cross_test/build.gradle.kts`, which reads [DBUSMOCK_REQUIRED_ENV] as its override.
 *
 * Callers must still `return` afterwards; only the JVM actual aborts the test by throwing.
 */
internal expect fun skipTest(reason: String)

/**
 * A running python-dbusmock process. [stop] terminates it and reaps the child.
 */
internal expect class DbusmockHandle {
    fun stop()
}

/**
 * Launches `python3 -m dbusmock` on the current (session) bus, claiming [busName] and exposing
 * a generic mock object at [objectPath] with main interface [interfaceName].
 *
 * @param objectManager When `true`, dbusmock is started with `-m` so that the mock object also
 *   implements `org.freedesktop.DBus.ObjectManager` (its `GetManagedObjects` reports the
 *   objects added below [objectPath] via the mock `AddObject` control call).
 * @param template When non-null, dbusmock is started with `-t [template]` instead of the
 *   NAME/PATH/INTERFACE positionals (the template dictates them — e.g. `bluez5` claims
 *   `org.bluez` at `/`). Templates may declare `SYSTEM_BUS = True` (bluez5 does); the explicit
 *   `--session` flag the harness always passes overrides that, keeping the mock on the private
 *   `dbus-run-session` bus that [com.monkopedia.sdbus.createBusConnection] connects to — no
 *   private system bus is needed. [busName]/[objectPath]/[interfaceName] are ignored in this
 *   mode (callers should pass the template's well-known values for readability).
 * @return a [DbusmockHandle] if the process started, or `null` if dbusmock / python is not
 *   available (the caller should then SKIP). Throws instead of returning `null` when
 *   [DBUSMOCK_REQUIRED_ENV] is set — see [dbusmockUnavailable].
 */
internal expect fun launchDbusmock(
    busName: String,
    objectPath: String,
    interfaceName: String,
    objectManager: Boolean = false,
    template: String? = null
): DbusmockHandle?
