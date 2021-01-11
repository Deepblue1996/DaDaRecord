package com.deep.recordscreen.core

import com.deep.dpwork.annotation.DpCrash
import com.deep.dpwork.annotation.DpDataBase
import com.deep.dpwork.annotation.DpLandscape
import com.deep.dpwork.annotation.DpLang
import com.deep.dpwork.core.kotlin.DpApplicationKt
import com.deep.dpwork.lang.LanguageType
import com.deep.recordscreen.data.AppData

/**
 * Class - 应用入口
 *
 * Created by Deepblue on 2018/9/29 0029.
 */
@DpCrash
@DpLandscape
@DpLang(LanguageType.LANGUAGE_CHINESE_SIMPLIFIED)
class InitApp : DpApplicationKt() {
    companion object {
        @DpDataBase(AppData::class)
        lateinit var appData: AppData
    }
}