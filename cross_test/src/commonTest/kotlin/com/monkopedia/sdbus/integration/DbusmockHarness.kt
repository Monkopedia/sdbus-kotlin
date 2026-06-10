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
 * ## Graceful skip
 *
 * If python3 / python3-dbusmock is not present, or no D-Bus session bus is reachable,
 * [launchDbusmock] returns `null` and the smoke test SKIPs cleanly (asserts nothing) rather
 * than failing — so CI without dbusmock still passes.
 */
internal expect fun dbusmockGetenv(name: String): String?

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
 * @return a [DbusmockHandle] if the process started, or `null` if dbusmock / python is not
 *   available (the caller should then SKIP).
 */
internal expect fun launchDbusmock(
    busName: String,
    objectPath: String,
    interfaceName: String
): DbusmockHandle?
