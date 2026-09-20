package org.example.project

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.Dispatchers

/**
 * 使用 quickjs-kt 执行当前工作区中的纯 JavaScript。
 *
 * 每次运行创建独立 QuickJS 上下文，执行完毕后关闭，
 * 避免不同文件之间残留全局变量。
 */
class QuickJsRunner : JsRunner {

    override suspend fun run(
        code: String,
        fileName: String
    ): JsRunOutput {
        val logs = mutableListOf<String>()
        val startedAt = System.currentTimeMillis()

        val quickJs = QuickJs.create(Dispatchers.Default)

        return try {
            quickJs.define("console") {
                function("log") { args ->
                    logs += formatArguments(args)
                }

                function("info") { args ->
                    logs += "[INFO] ${formatArguments(args)}"
                }

                function("warn") { args ->
                    logs += "[WARN] ${formatArguments(args)}"
                }

                function("error") { args ->
                    logs += "[ERROR] ${formatArguments(args)}"
                }

                function("debug") { args ->
                    logs += "[DEBUG] ${formatArguments(args)}"
                }
            }

            quickJs.function("print") { args ->
                logs += formatArguments(args)
            }

            val result = quickJs.evaluate<Any?>(
                code = code,
                filename = fileName
            )

            JsRunOutput(
                ok = true,
                logs = logs.toList(),
                result = result?.toString(),
                error = null,
                durationMs = System.currentTimeMillis() - startedAt
            )
        } catch (throwable: Throwable) {
            JsRunOutput(
                ok = false,
                logs = logs.toList(),
                result = null,
                error = throwable.message ?: throwable.toString(),
                durationMs = System.currentTimeMillis() - startedAt
            )
        } finally {
            quickJs.close()
        }
    }

    private fun formatArguments(
        args: Array<out Any?>
    ): String {
        return args.joinToString(separator = " ") { value ->
            when (value) {
                null -> "undefined"
                is String -> value
                else -> value.toString()
            }
        }
    }
}