package com.bitwise.plugin

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

class BitwisePluginConfigurable : Configurable {
    private lateinit var showDecimalBox: JCheckBox
    private lateinit var showHexBox: JCheckBox
    private lateinit var showBinaryBox: JCheckBox
    private lateinit var onlyBitwiseBox: JCheckBox
    private lateinit var panel: JPanel

    override fun getDisplayName(): @NlsContexts.ConfigurableName String {
        return "Bitwise Inlay Hints"
    }

    override fun createComponent(): JComponent {
        panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(4, 8)
        gbc.gridx = 0

        gbc.gridy = 0
        panel.add(JLabel("<html><b>Display formats in inlay hint:</b></html>"), gbc)

        gbc.gridy = 1
        showDecimalBox = JCheckBox("Decimal (e.g. 240)")
        panel.add(showDecimalBox, gbc)

        gbc.gridy = 2
        showHexBox = JCheckBox("Hexadecimal (e.g. 0x000000F0)")
        panel.add(showHexBox, gbc)

        gbc.gridy = 3
        showBinaryBox = JCheckBox("Binary (e.g. 0b0000_1111_0000_...)")
        panel.add(showBinaryBox, gbc)

        gbc.gridy = 4
        panel.add(JSeparator(), gbc)

        gbc.gridy = 5
        onlyBitwiseBox = JCheckBox("Show hints only when a bitwise operator is present")
        panel.add(onlyBitwiseBox, gbc)

        gbc.gridy = 6
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.VERTICAL
        panel.add(JPanel(), gbc) // spacer

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val s = BitwisePluginSettings.getInstance()
        return showDecimalBox.isSelected != s.isShowDecimal || showHexBox.isSelected != s.isShowHex || showBinaryBox.isSelected != s.isShowBinary || onlyBitwiseBox.isSelected != s.isShowOnlyBitwiseExpressions
    }

    override fun apply() {
        val s = BitwisePluginSettings.getInstance()
        s.isShowDecimal = (showDecimalBox.isSelected)
        s.isShowHex = (showHexBox.isSelected)
        s.isShowBinary = (showBinaryBox.isSelected)
        s.isShowOnlyBitwiseExpressions = (onlyBitwiseBox.isSelected)
    }

    override fun reset() {
        val s = BitwisePluginSettings.getInstance()
        showDecimalBox.setSelected(s.isShowDecimal)
        showHexBox.setSelected(s.isShowHex)
        showBinaryBox.setSelected(s.isShowBinary)
        onlyBitwiseBox.setSelected(s.isShowOnlyBitwiseExpressions)
    }
}
