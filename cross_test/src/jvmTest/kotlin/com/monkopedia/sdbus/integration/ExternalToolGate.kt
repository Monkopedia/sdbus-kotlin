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

import java.io.File
import org.junit.AssumptionViolatedException

/**
 * Environment variable that turns a "the external tool is not on PATH" skip into a hard failure.
 * Set by the CI job that installs those tools on purpose.
 *
 * Same shape, and the same reasoning, as `DBUSMOCK_REQUIRED` in `DbusmockHarness` (#182): the skip
 * is right on a contributor's box, where `busctl` / `dbus-monitor` may legitimately be absent, and
 * wrong in a job that guarantees them — there a vanished binary would silently convert the only
 * out-of-process coverage the repo has into a green `skipped="1"` (#229).
 */
internal const val EXTERNAL_TOOLS_REQUIRED_ENV = "EXTERNAL_TOOLS_REQUIRED"

/**
 * Returns the executable [name] found on `PATH`, or aborts the calling test.
 *
 * [capability] completes the sentence "<name> is not on PATH, so …" and should name the coverage
 * that is lost without the tool.
 *
 * @throws AssumptionViolatedException when the tool is absent — JUnit records that as a real
 *   `<skipped/>`, unlike an early return (#227).
 * @throws IllegalStateException instead, when [EXTERNAL_TOOLS_REQUIRED_ENV] is set.
 */
internal fun requireExternalTool(name: String, capability: String): File {
    val path = System.getenv("PATH").orEmpty()
    path.split(File.pathSeparator)
        .map { File(it, name) }
        .firstOrNull { it.canExecute() }
        ?.let { return it }

    val reason = "$name is not on PATH, so $capability."
    check(System.getenv(EXTERNAL_TOOLS_REQUIRED_ENV) == null) {
        "$EXTERNAL_TOOLS_REQUIRED_ENV is set but $reason"
    }
    throw AssumptionViolatedException(reason)
}
