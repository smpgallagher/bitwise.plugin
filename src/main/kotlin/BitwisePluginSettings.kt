package com.bitwise.plugin

import com.bitwise.plugin.BitwiseExpressionEvaluator.EvalResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "BitwisePluginSettings", storages = [Storage("BitwisePluginSettings.xml")])
class BitwisePluginSettings : PersistentStateComponent<BitwisePluginSettings.State?> {
    class State {
        var showDecimal: Boolean = true
        var showHex: Boolean = true
        var showBinary: Boolean = true
        var showOnlyBitwiseExpressions: Boolean = true // hide hints for plain literals
        var minOperands: Int = 1 // show hint if at least N operands involved
    }

    private var myState = State()

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState = state
    }

    var isShowDecimal: Boolean
        get() = myState.showDecimal
        set(v) {
            myState.showDecimal = v
        }
    var isShowHex: Boolean
        get() = myState.showHex
        set(v) {
            myState.showHex = v
        }
    var isShowBinary: Boolean
        get() = myState.showBinary
        set(v) {
            myState.showBinary = v
        }
    var isShowOnlyBitwiseExpressions: Boolean
        get() = myState.showOnlyBitwiseExpressions
        set(v) {
            myState.showOnlyBitwiseExpressions = v
        }

    /**
     * Build the hint string according to current settings.
     */
    fun buildHint(result: EvalResult): String {
        val sb = StringBuilder("= ")
        var first = true
        if (this.isShowDecimal) {
            sb.append(result.toDecimalString())
            first = false
        }
        if (this.isShowHex) {
            if (!first) sb.append(" | ")
            sb.append(result.toHexString())
            first = false
        }
        if (this.isShowBinary) {
            if (!first) sb.append(" | ")
            sb.append(result.toBinaryString())
        }
        return sb.toString()
    }

    companion object {
        fun getInstance(): BitwisePluginSettings {
            return ApplicationManager.getApplication()
                .getService(BitwisePluginSettings::class.java)
        }
    }
}
