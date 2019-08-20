/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import org.rust.cargo.project.toolwindow.CargoToolWindow
import org.rust.cargo.runconfig.hasCargoProject
import javax.swing.JTree

abstract class CargoTreeAction : AnAction(), DumbAware {

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.project?.hasCargoProject == true && getTree(e) != null
    }

    class CollapseAll : CargoTreeAction() {
        override fun actionPerformed(e: AnActionEvent) {
            val tree = getTree(e) ?: return
            for (row in tree.rowCount - 1 downTo 0) {
                tree.collapseRow(row)
            }
        }
    }

    class ExpandAll : CargoTreeAction() {
        override fun actionPerformed(e: AnActionEvent) {
            val tree = getTree(e) ?: return
            var row = 0
            while (row < tree.rowCount) {
                tree.expandRow(row)
                row++
            }
        }
    }

    companion object {
        private fun getTree(e: AnActionEvent): JTree? = e.getData(CargoToolWindow.CARGO_PROJECTS_TREE)
    }
}
