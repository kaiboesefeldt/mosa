package straightway.mosa

import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli
import straightway.mosa.commandline.ExportCommand
import straightway.mosa.commandline.ServeCommand
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    val parser = ArgParser("mosa")
    parser.subcommands(ServeCommand(), ExportCommand())
    parser.parse(args)
    exitProcess(1)
}
