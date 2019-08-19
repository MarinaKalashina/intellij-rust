/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

import com.intellij.build.BuildProgressListener
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.impl.*
import com.intellij.build.output.BuildOutputInstantReaderImpl
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import org.rust.cargo.CargoConstants
import org.rust.cargo.runconfig.RsAnsiEscapeDecoder.Companion.ANSI_SGR_RE
import org.rust.cargo.runconfig.RsAnsiEscapeDecoder.Companion.CSI
import org.rust.cargo.runconfig.RsExecutableRunner.Companion.binaries
import org.rust.cargo.runconfig.createFilters

@Suppress("UnstableApiUsage")
class CargoBuildAdapter(
    private val context: CargoBuildContext,
    private val buildProgressListener: BuildProgressListener
) : ProcessAdapter() {
    private val textBuffer: MutableList<String> = mutableListOf()
    private val buildOutputParser: CargoBuildEventsConverter = CargoBuildEventsConverter(context)
    private val instantReader = BuildOutputInstantReaderImpl(
        context.buildId,
        context.buildId,
        buildProgressListener,
        listOf(buildOutputParser)
    )

    init {
        val descriptor = DefaultBuildDescriptor(
            context.buildId,
            "Run Cargo command",
            context.workingDirectory.toString(),
            context.started
        )
        val buildStarted = StartBuildEventImpl(descriptor, "${context.taskName} running...")
            .withExecutionFilters(*createFilters(context.cargoProject).toTypedArray())
        buildProgressListener.onEvent(context.buildId, buildStarted)
    }

    override fun processTerminated(event: ProcessEvent) {
        instantReader.closeAndGetFuture().whenComplete { _, error ->
            val isSuccess = event.exitCode == 0 && context.errors == 0
            val isCanceled = context.indicator.isCanceled
            context.environment.binaries = buildOutputParser.binaries.takeIf { isSuccess && !isCanceled }

            val (status, result) = when {
                isCanceled -> "canceled" to SkippedResultImpl()
                isSuccess -> "successful" to SuccessResultImpl()
                else -> "failed" to FailureResultImpl(error)
            }
            val buildFinished = FinishBuildEventImpl(
                context.buildId,
                null,
                System.currentTimeMillis(),
                "${context.taskName} $status",
                result
            )
            buildProgressListener.onEvent(context.buildId, buildFinished)
            context.finished(isSuccess)

            val targetPath = context.workingDirectory.resolve(CargoConstants.ProjectLayout.target)
            val targetDir = VfsUtil.findFile(targetPath, true) ?: return@whenComplete
            VfsUtil.markDirtyAndRefresh(true, true, true, targetDir)
        }
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        textBuffer.add(event.text)
        if (!event.text.endsWith("\n")) return
        val text = textBuffer.joinToString("")
            .replace(ERASE_LINES_RE, "\n")
            .replace(BUILD_PROGRESS_RE) { it.value.trimEnd(' ', '\r', '\n') + "\n" }
        instantReader.append(text)
        textBuffer.clear()
    }

    companion object {
        private val ERASE_LINES_RE: Regex = """${StringUtil.escapeToRegexp(CSI)}\d?K""".toRegex()
        private val BUILD_PROGRESS_RE: Regex = """($ANSI_SGR_RE)* *Building($ANSI_SGR_RE)* \[ *=*>? *] \d+/\d+: [\w\-(.)]+(, [\w\-(.)]+)*( *[\r\n])*""".toRegex()
    }
}
