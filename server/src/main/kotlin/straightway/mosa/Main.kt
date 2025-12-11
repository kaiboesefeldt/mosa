package straightway.mosa

import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli
import kotlinx.coroutines.Dispatchers
import straightway.mosa.commandline.ExportCommand
import straightway.mosa.commandline.ImportCommand
import straightway.mosa.commandline.PostDumpStreamIO
import straightway.mosa.commandline.ServeCommand
import java.security.MessageDigest
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    val parser = ArgParser("mosa")
    val postDumpStreamIO = PostDumpStreamIO(Dispatchers.IO)
    parser.subcommands(
        ServeCommand(),
        ExportCommand(postDumpStreamIO, System.out),
        ImportCommand(postDumpStreamIO, System.`in`) { MessageDigest.getInstance("SHA-256") },
    )
    parser.parse(args)
    exitProcess(1)
}
