package com.deep.recordscreen.dialog

import com.deep.dpwork.annotation.DpNullAnim
import com.deep.dpwork.core.kotlin.DialogScreenKt
import com.deep.recordscreen.databinding.BlueLoadingLayoutBinding

@DpNullAnim
class TextToastDialogScreen : DialogScreenKt<BlueLoadingLayoutBinding>() {

    private var msg = ""
    override fun mainInit() {
        here.connectText.text = msg
    }

    fun setConnectText(msg: String) {
        this.msg = msg
    }

}