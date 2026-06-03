package com.bitwise.plugin

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Inspection to ensure that all companion constants in an @AutoMask class have unique values.
 * It uses the BitwiseExpressionEvaluator to resolve complex bitwise math.
 */
class AutoMaskUniqueValueInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitProperty(property: KtProperty) {
            super.visitProperty(property)

            val companionObject = property.getStrictParentOfType<KtObjectDeclaration>()
                ?.takeIf { it.isCompanion() }
                ?: return
            val maskClass = companionObject.getStrictParentOfType<KtClass>()
                ?.takeIf { it.hasAutoMaskAnnotation() }
                ?: return

            val initializer = property.initializer ?: return
            val rawValue = BitwiseExpressionEvaluator.evaluateKotlin(initializer) ?: return
            val currentValue = (rawValue as? Number)?.toLong() ?: return

            val duplicate = companionObject.body?.properties
                .orEmpty()
                .asSequence()
                .filter { it !== property && it.textOffset < property.textOffset }
                .firstOrNull { other ->
                    val otherRaw = other.initializer?.let { BitwiseExpressionEvaluator.evaluateKotlin(it) }
                    (otherRaw as? Number)?.toLong() == currentValue
                }

            if (duplicate != null) {
                val otherName = duplicate.name ?: "another constant"
                holder.registerProblem(
                    property.nameIdentifier ?: property,
                    "Duplicate @AutoMask value: $currentValue in '${maskClass.name}' (conflicts with '$otherName')",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                )
            }
        }
    }

    private fun KtClass.hasAutoMaskAnnotation(): Boolean {
        return annotationEntries.any {
            it.shortName?.asString() == "AutoMask"
        }
    }
}
