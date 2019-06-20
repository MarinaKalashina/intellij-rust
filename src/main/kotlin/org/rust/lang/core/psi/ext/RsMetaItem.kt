/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.resolve.ref.deriveReference
import org.rust.lang.core.stubs.RsMetaItemStub

val RsMetaItem.name: String? get() = referenceName

val RsMetaItem.value: String? get() = litExpr?.stringValue

val RsMetaItem.hasEq: Boolean get() = greenStub?.hasEq ?: (eq != null)

fun RsMetaItem.resolveToDerivedTrait(): RsTraitItem? =
    deriveReference?.resolve() as? RsTraitItem

abstract class RsMetaItemImplMixin : RsStubbedElementImpl<RsMetaItemStub>, RsMetaItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsMetaItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val referenceNameElement: PsiElement? get() = compositeName

    override val referenceName: String?
        get() = greenStub?.name ?: compositeName?.text?.replace(" ", "")

    override fun getReference(): RsReference? = references.firstOrNull { it is RsReference } as? RsReference

    override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)
}
