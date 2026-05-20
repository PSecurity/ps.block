package com.psecurity.psblock.root

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Executa comandos como root via shell su.
 * Timeout curto por sessão para não bloquear o serviço.
 */
object RootExecutor {

    private const val TAG = "PS_ROOT"
    private const val TIMEOUT_SEC = 5L

    data class RootResult(val success: Boolean, val output: String, val error: String)

    fun isRootAvailable(): Boolean {
        return try {
            val p  = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(p.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            val finished = p.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!finished) { p.destroyForcibly(); return false }
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            out.contains("uid=0")
        } catch (e: Exception) {
            Log.e(TAG, "Root check falhou: ${e.message}")
            false
        }
    }

    fun exec(command: String): RootResult = execAll(listOf(command))

    fun execAll(commands: List<String>): RootResult {
        return try {
            val p  = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(p.outputStream)
            commands.forEach { cmd ->
                Log.d(TAG, "CMD: $cmd")
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()

            val finished = p.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                Log.w(TAG, "Root timeout após ${TIMEOUT_SEC}s")
                return RootResult(false, "", "timeout")
            }

            val out  = BufferedReader(InputStreamReader(p.inputStream)).readText()
            val err  = BufferedReader(InputStreamReader(p.errorStream)).readText()
            val code = try { p.exitValue() } catch (_: Exception) { -1 }

            // exit 0 ou comandos com || true retornam 0; considerar sucesso se não houver exceção
            val ok = code == 0 || commands.all { it.trimEnd().endsWith("|| true") }
            RootResult(ok, out, err)
        } catch (e: Exception) {
            Log.e(TAG, "Exceção root: ${e.message}")
            RootResult(false, "", e.message ?: "erro")
        }
    }
}
