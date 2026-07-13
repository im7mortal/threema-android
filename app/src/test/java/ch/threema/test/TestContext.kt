package ch.threema.test

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import io.mockk.every
import io.mockk.mockk

object TestContext {
    fun create(block: Context.() -> Unit = {}): Context = mockk {
        every { getString(any()) } answers { TestContext.getString(firstArg()) }
        every { getString(any(), *anyVararg<Any>()) } answers {
            TestContext.getString(firstArg(), *secondArg<Array<Any?>>())
        }
        every { resources } returns mockk {
            every { getQuantityString(any(), any(), *anyVararg<Any>()) } answers {
                TestContext.getQuantityString(firstArg(), secondArg(), *thirdArg<Array<Any?>>())
            }
        }
        block()
    }

    fun getString(@StringRes id: Int): String =
        "[$id]"

    fun getString(@StringRes id: Int, vararg args: Any?): String =
        "[$id] with args (${args.joinToString()})"

    fun getQuantityString(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
        "[$id] with quantity=$quantity and args (${args.joinToString()})"
}
