package com.bitwise.plugin

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.*
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.JPanel
@Suppress("UnstableApiUsage")
class BitwiseInlayHintsProvider : InlayHintsProvider<NoSettings> {
    private val log = Logger.getInstance(BitwiseInlayHintsProvider::class.java)

    override val key: SettingsKey<NoSettings> = SettingsKey("bitwise.inlay.hints")

    @get:Nls(capitalization = Nls.Capitalization.Sentence)
    override val name: String = "Bitwise constant expressions"

    override val previewText: String = """
        val x = 1 or 2
        val y = 0xFF and 0x0F.inv()
        int j = 1 | 2;
    """.trimIndent()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JComponent = JPanel()
    }

    override fun createSettings(): NoSettings = NoSettings()

    override val isVisibleInSettings: Boolean = true

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                // 1. Handle Java Expressions
                if (element is PsiExpression && isBitwiseJava(element)) {
                    // Avoid nested hints by checking if parent is also a bitwise expr
                    if (element.parent is PsiExpression && isBitwiseJava(element.parent as PsiExpression)) return true
                    try {
                        tryAnnotateJava(element, sink)
                    } catch (e: Exception) {
                        log.warn("Failed to annotate Java bitwise expression: ${element.text}", e)
                    }
                }

                // 2. Handle Kotlin Expressions
                else if (element is KtExpression && isBitwiseKotlin(element)) {
                    // Avoid nested hints
                    val parent = element.parent
                    if (parent is KtExpression && isBitwiseKotlin(parent)) return true
                    try {
                        tryAnnotateKotlin(element, sink)
                    } catch (e: Exception) {
                        log.warn("Failed to annotate Kotlin bitwise expression: ${element.text}", e)
                    }
                }

                return true
            }

            private fun tryAnnotateJava(expr: PsiExpression, sink: InlayHintsSink) {
                val evalHelper = JavaPsiFacade.getInstance(expr.project).constantEvaluationHelper
                val value = evalHelper.computeConstantExpression(expr) ?: return
                applyHint(expr, value, sink)
            }

            private fun tryAnnotateKotlin(expr: KtExpression, sink: InlayHintsSink) {
                val value = BitwiseExpressionEvaluator.evaluateKotlin(expr) ?: return
                applyHint(expr, value, sink)
            }

            private fun applyHint(element: PsiElement, value: Any, sink: InlayHintsSink) {
                val numericValue = when (value) {
                    is Long -> value
                    is Int -> value.toLong()
                    else -> return
                }

                val result = BitwiseExpressionEvaluator.EvalResult(numericValue, value is Long)
                val hintString = BitwisePluginSettings.getInstance().buildHint(result)
                val textPresentation = factory.smallText(hintString)
                val basePresentation = UnderlinedPresentation(factory.roundWithBackground(textPresentation))
                val presentation = factory.mouseHandling(
                    basePresentation,
                    InlayPresentationFactory.ClickListener { event, _ ->
                        if (event.mouseButton == com.intellij.codeInsight.hints.presentation.MouseButton.Left && event.clickCount == 2) {
                            CopyPasteManager.copyTextToClipboard(hintString.drop(2))
                        }
                    },
                    null
                )
                sink.addInlineElement(element.textRange.endOffset, true, presentation, false)
            }

            private fun isBitwiseJava(expr: PsiExpression): Boolean {
                return when (expr) {
                    is PsiBinaryExpression -> BITWISE_JAVA_OPS.contains(expr.operationSign.text)
                    is PsiPrefixExpression -> expr.operationSign.text == "~"
                    is PsiPolyadicExpression -> BITWISE_JAVA_OPS.contains(expr.operands.firstOrNull()?.text ?: "")
                    is PsiParenthesizedExpression -> expr.expression?.let { isBitwiseJava(it) } ?: false
                    else -> false
                }
            }

            private fun isBitwiseKotlin(
                expr: KtExpression,
                visited: MutableSet<KtProperty> = mutableSetOf()
            ): Boolean {
                return when (expr) {
                    is KtBinaryExpression -> {
                        val op = expr.operationReference.getReferencedName()
                        BITWISE_KOTLIN_OPS.contains(op)
                    }
                    is KtDotQualifiedExpression -> {
                        val selector = expr.selectorExpression
                        when (selector) {
                            is KtCallExpression -> BITWISE_KOTLIN_CALLS.contains(selector.calleeExpression?.text)
                            is KtNameReferenceExpression -> resolvesToBitwiseProperty(selector, visited)
                            else -> false
                        }
                    }
                    is KtPrefixExpression -> expr.operationToken == org.jetbrains.kotlin.lexer.KtTokens.MINUS &&
                        expr.baseExpression?.let { isBitwiseKotlin(it, visited) } == true
                    is KtParenthesizedExpression -> expr.expression?.let { isBitwiseKotlin(it, visited) } ?: false
                    else -> false
                }
            }

            private fun resolvesToBitwiseProperty(
                reference: KtNameReferenceExpression,
                visited: MutableSet<KtProperty>
            ): Boolean {
                val property = reference.references.firstNotNullOfOrNull { it.resolve() as? KtProperty } ?: return false
                if (!visited.add(property)) return false
                val result = property.initializer?.let { isBitwiseKotlin(it, visited) } == true
                visited.remove(property)
                return result
            }
        }
    }

    // UPDATED: Support both languages
    override fun isLanguageSupported(language: Language): Boolean {
        return language.id == "JAVA" || language.id.lowercase() == "kotlin"
    }

    companion object {
        private val BITWISE_JAVA_OPS = setOf("&", "|", "^", "<<", ">>", ">>>")
        private val BITWISE_KOTLIN_OPS = setOf("and", "or", "xor", "shl", "shr", "ushr")
        private val BITWISE_KOTLIN_CALLS = BITWISE_KOTLIN_OPS + "inv"
    }
}
@Suppress("UnstableApiUsage")
private class UnderlinedPresentation(
    private val delegate: com.intellij.codeInsight.hints.presentation.InlayPresentation
) : com.intellij.codeInsight.hints.presentation.BasePresentation() {

    override val width: Int = delegate.width

    override val height: Int = delegate.height

    override fun paint(g: Graphics2D, attributes: TextAttributes) {
        delegate.paint(g, attributes)
        val y = (height - 2).coerceAtLeast(0)
        val underlineStart = g.fontMetrics.stringWidth("   = ")
        val end = g.fontMetrics.stringWidth("  ")
        val originalColor = g.color
        g.color = attributes.foregroundColor ?: originalColor
        g.drawLine(underlineStart, y, (width - end).coerceAtLeast(1), y)
        g.color = originalColor
    }
    override fun toString(): String {
        return delegate.toString()
    }
}
